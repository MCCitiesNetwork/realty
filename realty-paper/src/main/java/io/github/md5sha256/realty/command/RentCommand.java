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
                .literal("rent")
                .permission("realty.command.rent")
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
        if (region.region().getOwners().contains(sender.getUniqueId())) {
            sender.sendMessage(messages.messageFor(MessageKeys.RENT_IS_LANDLORD,
                    Placeholder.unparsed("region", regionId)));
            return;
        }
        CompletableFuture.supplyAsync(() -> {
            try {
                RealtyLogicImpl.RentResult result = logic.rentRegion(
                        regionId, region.world().getUID(), sender.getUniqueId());
                return switch (result) {
                    case RealtyLogicImpl.RentResult.Success success -> {
                        Map<String, String> placeholders = logic.getRegionPlaceholders(regionId, region.world().getUID());
                        yield Map.entry(success, placeholders);
                    }
                    case RealtyLogicImpl.RentResult.NoLeaseContract ignored -> {
                        sender.sendMessage(messages.messageFor(MessageKeys.RENT_NO_LEASE_CONTRACT,
                                Placeholder.unparsed("region", regionId)));
                        yield null;
                    }
                    case RealtyLogicImpl.RentResult.AlreadyOccupied ignored -> {
                        sender.sendMessage(messages.messageFor(MessageKeys.RENT_ALREADY_OCCUPIED,
                                Placeholder.unparsed("region", regionId)));
                        yield null;
                    }
                    case RealtyLogicImpl.RentResult.UpdateFailed ignored -> {
                        sender.sendMessage(messages.messageFor(MessageKeys.RENT_UPDATE_FAILED,
                                Placeholder.unparsed("region", regionId)));
                        yield null;
                    }
                };
            } catch (Exception ex) {
                sender.sendMessage(messages.messageFor(MessageKeys.RENT_ERROR,
                        Placeholder.unparsed("error", ex.getMessage())));
                return null;
            }
        }, executorState.dbExec()).thenAcceptAsync(entry -> {
            if (entry == null) {
                return;
            }
            RealtyLogicImpl.RentResult.Success success = entry.getKey();
            double price = success.price();
            double balance = economy.getBalance(sender);
            if (balance < price) {
                sender.sendMessage(messages.messageFor(MessageKeys.RENT_INSUFFICIENT_FUNDS,
                        Placeholder.unparsed("balance", CurrencyFormatter.format(balance)),
                        Placeholder.unparsed("price", CurrencyFormatter.format(price))));
                return;
            }
            EconomyResponse response = economy.withdrawPlayer(sender, price);
            if (!response.transactionSuccess()) {
                sender.sendMessage(messages.messageFor(MessageKeys.RENT_PAYMENT_FAILED,
                        Placeholder.unparsed("error", response.errorMessage)));
                return;
            }
            OfflinePlayer landlord = Bukkit.getOfflinePlayer(success.landlordId());
            economy.depositPlayer(landlord, price);
            ProtectedRegion protectedRegion = region.region();
            protectedRegion.getOwners().addPlayer(sender.getUniqueId());
            regionProfileService.applyFlags(region, RegionState.LEASED, entry.getValue());
            signTextApplicator.updateLoadedSigns(region.world(), regionId, RegionState.LEASED, entry.getValue());
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
