package io.github.md5sha256.realty.command;

import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import io.github.md5sha256.realty.api.CurrencyFormatter;
import io.github.md5sha256.realty.api.NotificationService;
import io.github.md5sha256.realty.api.RegionProfileService;
import io.github.md5sha256.realty.api.RegionState;
import io.github.md5sha256.realty.api.SignTextApplicator;
import io.github.md5sha256.realty.command.util.WorldGuardRegion;
import io.github.md5sha256.realty.command.util.WorldGuardRegionResolver;
import io.github.md5sha256.realty.database.entity.LeaseholdContractEntity;
import io.github.md5sha256.realty.api.RealtyApi;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.localisation.MessageKeys;
import io.github.md5sha256.realty.util.ExecutorState;
import org.incendo.cloud.paper.util.sender.Source;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Handles {@code /realty unrent [region]}.
 *
 * <p>Removes the tenant from a leased region, clears WorldGuard members,
 * provides a prorated refund, and updates the region sign.
 * Permission: {@code realty.command.unrent}.</p>
 */
public record UnrentCommand(
        @NotNull ExecutorState executorState,
        @NotNull RealtyApi logic,
        @NotNull Economy economy,
        @NotNull NotificationService notificationService,
        @NotNull RegionProfileService regionProfileService,
        @NotNull SignTextApplicator signTextApplicator,
        @NotNull MessageContainer messages
) implements CustomCommandBean.Single {

    @Override
    public @NotNull Command<? extends Source> command(@NotNull Command.Builder<Source> builder) {
        return builder
                .literal("unrent")
                .permission("realty.command.unrent")
                .optional("region", WorldGuardRegionResolver.worldGuardRegionResolver())
                .handler(this::execute)
                .build();
    }

    private void execute(@NotNull CommandContext<Source> ctx) {
        if (!(ctx.sender().source() instanceof Player sender)) {
            ctx.sender().source().sendMessage(messages.messageFor(MessageKeys.COMMON_PLAYERS_ONLY));
            return;
        }
        WorldGuardRegion region = ctx.<WorldGuardRegion>optional("region")
                .orElseGet(() -> WorldGuardRegionResolver.resolveAtLocation(sender.getLocation()));
        if (region == null) {
            sender.sendMessage(messages.messageFor(MessageKeys.ERROR_NO_REGION));
            return;
        }
        String regionId = region.region().getId();
        if (!region.region().getOwners().contains(sender.getUniqueId())) {
            sender.sendMessage(messages.messageFor(MessageKeys.UNRENT_NOT_TENANT,
                    Placeholder.unparsed("region", regionId)));
            return;
        }
        // Step 1: query lease to compute refund (DB read, no mutation)
        CompletableFuture.supplyAsync(() -> {
            try {
                return logic.getLeaseholdContract(regionId, region.world().getUID());
            } catch (Exception ex) {
                sender.sendMessage(messages.messageFor(MessageKeys.UNRENT_ERROR,
                        Placeholder.unparsed("error", ex.getMessage())));
                return null;
            }
        }, executorState.dbExec()).thenAcceptAsync(lease -> {
            if (lease == null) {
                sender.sendMessage(messages.messageFor(MessageKeys.UNRENT_NO_LEASEHOLD_CONTRACT,
                        Placeholder.unparsed("region", regionId)));
                return;
            }
            long totalSeconds = lease.durationSeconds();
            long remainingSeconds = lease.endDate() == null ? 0
                    : Math.max(0, java.time.Duration.between(java.time.LocalDateTime.now(), lease.endDate()).getSeconds());
            double refund = totalSeconds > 0 ? lease.price() * remainingSeconds / totalSeconds : 0;
            // Step 2: economy — withdraw from landlord, deposit to tenant (main thread)
            if (refund > 0) {
                OfflinePlayer landlord = Bukkit.getOfflinePlayer(lease.landlordId());
                EconomyResponse withdrawResponse = economy.withdrawPlayer(landlord, refund);
                if (!withdrawResponse.transactionSuccess()) {
                    sender.sendMessage(messages.messageFor(MessageKeys.UNRENT_REFUND_FAILED,
                            Placeholder.unparsed("error", withdrawResponse.errorMessage)));
                    return;
                }
                EconomyResponse depositResponse = economy.depositPlayer(sender, refund);
                if (!depositResponse.transactionSuccess()) {
                    economy.depositPlayer(landlord, refund);
                    sender.sendMessage(messages.messageFor(MessageKeys.UNRENT_REFUND_FAILED,
                            Placeholder.unparsed("error", depositResponse.errorMessage)));
                    return;
                }
            }
            // Step 3: DB mutation
            CompletableFuture.supplyAsync(() -> {
                RealtyApi.UnrentResult result = logic.unrentRegion(
                        regionId, region.world().getUID(), sender.getUniqueId());
                if (result instanceof RealtyApi.UnrentResult.Success) {
                    Map<String, String> placeholders = logic.getRegionPlaceholders(regionId, region.world().getUID());
                    return Map.entry(result, placeholders);
                }
                return Map.<RealtyApi.UnrentResult, Map<String, String>>entry(result, Map.of());
            }, executorState.dbExec()).thenAcceptAsync(entry -> {
                // Step 4: finalize or revert economy
                switch (entry.getKey()) {
                    case RealtyApi.UnrentResult.Success ignored -> {
                        ProtectedRegion protectedRegion = region.region();
                        protectedRegion.getOwners().clear();
                        protectedRegion.getMembers().clear();
                        regionProfileService.applyFlags(region, RegionState.FOR_LEASE, entry.getValue());
                        signTextApplicator.updateLoadedSigns(region.world(), regionId, RegionState.FOR_LEASE, entry.getValue());
                        sender.sendMessage(messages.messageFor(MessageKeys.UNRENT_SUCCESS,
                                Placeholder.unparsed("region", regionId),
                                Placeholder.unparsed("refund", CurrencyFormatter.format(refund))));
                        notificationService.queueNotification(lease.landlordId(),
                                messages.messageFor(MessageKeys.NOTIFICATION_REGION_UNRENTED,
                                        Placeholder.unparsed("player", sender.getName()),
                                        Placeholder.unparsed("region", regionId),
                                        Placeholder.unparsed("refund", CurrencyFormatter.format(refund))));
                    }
                    case RealtyApi.UnrentResult.NoLeaseholdContract ignored -> {
                        revertEconomy(sender, lease, refund);
                        sender.sendMessage(messages.messageFor(MessageKeys.UNRENT_NO_LEASEHOLD_CONTRACT,
                                Placeholder.unparsed("region", regionId)));
                    }
                    case RealtyApi.UnrentResult.UpdateFailed ignored -> {
                        revertEconomy(sender, lease, refund);
                        sender.sendMessage(messages.messageFor(MessageKeys.UNRENT_UPDATE_FAILED,
                                Placeholder.unparsed("region", regionId)));
                    }
                }
            }, executorState.mainThreadExec()).exceptionally(ex -> {
                executorState.mainThreadExec().execute(() -> {
                    revertEconomy(sender, lease, refund);
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    sender.sendMessage(messages.messageFor(MessageKeys.UNRENT_ERROR,
                            Placeholder.unparsed("error", cause.getMessage())));
                });
                return null;
            });
        }, executorState.mainThreadExec());
    }

    private void revertEconomy(@NotNull Player tenant,
                               @NotNull LeaseholdContractEntity lease,
                               double refund) {
        if (refund > 0) {
            economy.withdrawPlayer(tenant, refund);
            OfflinePlayer landlord = Bukkit.getOfflinePlayer(lease.landlordId());
            economy.depositPlayer(landlord, refund);
        }
    }

}
