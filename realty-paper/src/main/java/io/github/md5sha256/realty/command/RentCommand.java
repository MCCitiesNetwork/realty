package io.github.md5sha256.realty.command;

import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import io.github.md5sha256.realty.api.CurrencyFormatter;
import io.github.md5sha256.realty.api.DurationFormatter;
import io.github.md5sha256.realty.api.NotificationService;
import io.github.md5sha256.realty.api.RegionProfileService;
import io.github.md5sha256.realty.api.RegionState;
import io.github.md5sha256.realty.api.SignTextApplicator;
import io.github.md5sha256.realty.command.util.WorldGuardRegion;
import io.github.md5sha256.realty.command.util.WorldGuardRegionResolver;
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

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Handles {@code /realty rent <region>}.
 *
 * <p>Permission: {@code realty.command.rent}.</p>
 */
public record RentCommand(
        @NotNull ExecutorState executorState,
        @NotNull RealtyApi logic,
        @NotNull Economy economy,
        @NotNull NotificationService notificationService,
        @NotNull RegionProfileService regionProfileService,
        @NotNull SignTextApplicator signTextApplicator,
        @NotNull MessageContainer messages
) implements CustomCommandBean.Single {

    @Override
    public @NotNull Command<Source> command(@NotNull Command.Builder<Source> builder) {
        return builder
                .literal("rent")
                .permission("realty.command.rent")
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
        // Step 1: preview rent eligibility (DB, no mutation)
        CompletableFuture.supplyAsync(() -> {
            try {
                return logic.previewRent(regionId, region.world().getUID());
            } catch (Exception ex) {
                sender.sendMessage(messages.messageFor(MessageKeys.RENT_ERROR,
                        Placeholder.unparsed("error", ex.getMessage())));
                return null;
            }
        }, executorState.dbExec()).thenAcceptAsync(preview -> {
            if (preview == null) {
                return;
            }
            switch (preview) {
                case RealtyApi.RentResult.NoLeaseholdContract ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.RENT_NO_LEASEHOLD_CONTRACT,
                                Placeholder.unparsed("region", regionId)));
                case RealtyApi.RentResult.AlreadyOccupied ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.RENT_ALREADY_OCCUPIED,
                                Placeholder.unparsed("region", regionId)));
                case RealtyApi.RentResult.UpdateFailed ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.RENT_UPDATE_FAILED,
                                Placeholder.unparsed("region", regionId)));
                case RealtyApi.RentResult.Success success -> {
                    // Step 2: balance check + payment (main thread)
                    double price = success.price();
                    double balance = economy.getBalance(sender);
                    if (balance < price) {
                        sender.sendMessage(messages.messageFor(MessageKeys.RENT_INSUFFICIENT_FUNDS,
                                Placeholder.unparsed("balance", CurrencyFormatter.format(balance)),
                                Placeholder.unparsed("price", CurrencyFormatter.format(price))));
                        return;
                    }
                    if (price > 0) {
                        EconomyResponse response = economy.withdrawPlayer(sender, price);
                        if (!response.transactionSuccess()) {
                            sender.sendMessage(messages.messageFor(MessageKeys.RENT_PAYMENT_FAILED,
                                    Placeholder.unparsed("error", response.errorMessage)));
                            return;
                        }
                        OfflinePlayer landlord = Bukkit.getOfflinePlayer(success.landlordId());
                        economy.depositPlayer(landlord, price);
                    }
                    // Step 3: execute DB mutation
                    CompletableFuture.supplyAsync(() -> {
                        try {
                            RealtyApi.RentResult result = logic.rentRegion(
                                    regionId, region.world().getUID(), sender.getUniqueId());
                            if (result instanceof RealtyApi.RentResult.Success) {
                                return logic.getRegionPlaceholders(regionId, region.world().getUID());
                            }
                            return null;
                        } catch (Exception ex) {
                            return null;
                        }
                    }, executorState.dbExec()).thenAcceptAsync(placeholders -> {
                        // Step 4: finalize or refund
                        if (placeholders == null) {
                            if (price > 0) {
                                economy.depositPlayer(sender, price);
                            }
                            sender.sendMessage(messages.messageFor(MessageKeys.RENT_UPDATE_FAILED,
                                    Placeholder.unparsed("region", regionId)));
                            return;
                        }
                        ProtectedRegion protectedRegion = region.region();
                        protectedRegion.getOwners().clear();
                        protectedRegion.getMembers().clear();
                        protectedRegion.getOwners().addPlayer(sender.getUniqueId());
                        regionProfileService.applyFlags(region, RegionState.LEASED, placeholders);
                        signTextApplicator.updateLoadedSigns(region.world(), regionId, RegionState.LEASED, placeholders);
                        sender.sendMessage(messages.messageFor(MessageKeys.RENT_SUCCESS,
                                Placeholder.unparsed("region", regionId),
                                Placeholder.unparsed("price", CurrencyFormatter.format(price)),
                                Placeholder.unparsed("duration",
                                        DurationFormatter.format(Duration.ofSeconds(success.durationSeconds())))));
                        notificationService.queueNotification(success.landlordId(),
                                messages.messageFor(MessageKeys.NOTIFICATION_REGION_RENTED,
                                        Placeholder.unparsed("player", sender.getName()),
                                        Placeholder.unparsed("price", CurrencyFormatter.format(price)),
                                        Placeholder.unparsed("region", regionId)));
                    }, executorState.mainThreadExec());
                }
            }
        }, executorState.mainThreadExec());
    }

}
