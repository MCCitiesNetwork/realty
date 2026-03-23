package io.github.md5sha256.realty.command;

import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import io.github.md5sha256.realty.api.CurrencyFormatter;
import io.github.md5sha256.realty.api.NotificationService;
import io.github.md5sha256.realty.api.RegionProfileService;
import io.github.md5sha256.realty.api.RegionState;
import io.github.md5sha256.realty.api.SignTextApplicator;
import io.github.md5sha256.realty.command.util.WorldGuardRegion;
import io.github.md5sha256.realty.command.util.WorldGuardRegionResolver;
import io.github.md5sha256.realty.database.RealtyLogicImpl;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.localisation.MessageKeys;
import io.github.md5sha256.realty.util.ExecutorState;
import io.papermc.paper.command.brigadier.CommandSourceStack;
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
        @NotNull RealtyLogicImpl logic,
        @NotNull Economy economy,
        @NotNull NotificationService notificationService,
        @NotNull RegionProfileService regionProfileService,
        @NotNull SignTextApplicator signTextApplicator,
        @NotNull MessageContainer messages
) implements CustomCommandBean.Single {

    @Override
    public @NotNull Command<CommandSourceStack> command(@NotNull Command.Builder<CommandSourceStack> builder) {
        return builder
                .literal("unrent")
                .permission("realty.command.unrent")
                .optional("region", WorldGuardRegionResolver.worldGuardRegionResolver())
                .handler(this::execute)
                .build();
    }

    private void execute(@NotNull CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.sender().getSender() instanceof Player sender)) {
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
        CompletableFuture.supplyAsync(() -> {
            try {
                RealtyLogicImpl.UnrentResult result = logic.unrentRegion(
                        regionId, region.world().getUID(), sender.getUniqueId());
                return switch (result) {
                    case RealtyLogicImpl.UnrentResult.Success success -> {
                        Map<String, String> placeholders = logic.getRegionPlaceholders(regionId, region.world().getUID());
                        yield Map.entry(success, placeholders);
                    }
                    case RealtyLogicImpl.UnrentResult.NoLeaseContract ignored -> {
                        sender.sendMessage(messages.messageFor(MessageKeys.UNRENT_NO_LEASE_CONTRACT,
                                Placeholder.unparsed("region", regionId)));
                        yield null;
                    }
                    case RealtyLogicImpl.UnrentResult.UpdateFailed ignored -> {
                        sender.sendMessage(messages.messageFor(MessageKeys.UNRENT_UPDATE_FAILED,
                                Placeholder.unparsed("region", regionId)));
                        yield null;
                    }
                };
            } catch (Exception ex) {
                sender.sendMessage(messages.messageFor(MessageKeys.UNRENT_ERROR,
                        Placeholder.unparsed("error", ex.getMessage())));
                return null;
            }
        }, executorState.dbExec()).thenAcceptAsync(entry -> {
            if (entry == null) {
                return;
            }
            RealtyLogicImpl.UnrentResult.Success success = entry.getKey();
            double refund = success.refund();
            if (refund > 0) {
                EconomyResponse response = economy.depositPlayer(sender, refund);
                if (!response.transactionSuccess()) {
                    sender.sendMessage(messages.messageFor(MessageKeys.UNRENT_REFUND_FAILED,
                            Placeholder.unparsed("error", response.errorMessage)));
                    return;
                }
                OfflinePlayer landlord = Bukkit.getOfflinePlayer(success.landlordId());
                economy.withdrawPlayer(landlord, refund);
            }
            ProtectedRegion protectedRegion = region.region();
            protectedRegion.getOwners().clear();
            protectedRegion.getMembers().clear();
            protectedRegion.getOwners().addPlayer(success.landlordId());
            regionProfileService.applyFlags(region, RegionState.FOR_LEASE, entry.getValue());
            signTextApplicator.updateLoadedSigns(region.world(), regionId, RegionState.FOR_LEASE, entry.getValue());
            sender.sendMessage(messages.messageFor(MessageKeys.UNRENT_SUCCESS,
                    Placeholder.unparsed("region", regionId),
                    Placeholder.unparsed("refund", CurrencyFormatter.format(refund))));
            notificationService.queueNotification(success.landlordId(),
                    messages.messageFor(MessageKeys.NOTIFICATION_REGION_UNRENTED,
                            Placeholder.unparsed("player", sender.getName()),
                            Placeholder.unparsed("region", regionId),
                            Placeholder.unparsed("refund", CurrencyFormatter.format(refund))));
        }, executorState.mainThreadExec());
    }

}
