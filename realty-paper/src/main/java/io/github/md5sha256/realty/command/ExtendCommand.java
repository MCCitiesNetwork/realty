package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.api.CurrencyFormatter;
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

import java.util.concurrent.CompletableFuture;

/**
 * Handles {@code /realty extend <region>}.
 *
 * <p>Permission: {@code realty.command.extend}.</p>
 */
public record ExtendCommand(
        @NotNull ExecutorState executorState,
        @NotNull RealtyApi logic,
        @NotNull Economy economy,
        @NotNull SignTextApplicator signTextApplicator,
        @NotNull MessageContainer messages
) implements CustomCommandBean.Single {

    @Override
    public @NotNull Command<? extends Source> command(@NotNull Command.Builder<Source> builder) {
        return builder
                .literal("extend")
                .permission("realty.command.extend")
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
        CompletableFuture.supplyAsync(() -> {
            try {
                return logic.previewRenewLeasehold(regionId, region.world().getUID());
            } catch (Exception ex) {
                sender.sendMessage(messages.messageFor(MessageKeys.EXTEND_ERROR,
                        Placeholder.unparsed("error", ex.getMessage())));
                return null;
            }
        }, executorState.dbExec()).thenAcceptAsync(preview -> {
            if (preview == null) {
                return;
            }
            switch (preview) {
                case RealtyApi.RenewLeaseholdResult.NoLeaseholdContract ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.EXTEND_NO_LEASEHOLD_CONTRACT,
                                Placeholder.unparsed("region", regionId)));
                case RealtyApi.RenewLeaseholdResult.NoExtensionsRemaining ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.EXTEND_NO_EXTENSIONS,
                                Placeholder.unparsed("region", regionId)));
                case RealtyApi.RenewLeaseholdResult.UpdateFailed ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.EXTEND_UPDATE_FAILED,
                                Placeholder.unparsed("region", regionId)));
                case RealtyApi.RenewLeaseholdResult.Success success -> {
                    double price = success.price();
                    double balance = economy.getBalance(sender);
                    if (balance < price) {
                        sender.sendMessage(messages.messageFor(MessageKeys.EXTEND_INSUFFICIENT_FUNDS,
                                Placeholder.unparsed("balance", CurrencyFormatter.format(balance)),
                                Placeholder.unparsed("price", CurrencyFormatter.format(price))));
                        return;
                    }
                    if (price > 0) {
                        EconomyResponse response = economy.withdrawPlayer(sender, price);
                        if (!response.transactionSuccess()) {
                            sender.sendMessage(messages.messageFor(MessageKeys.EXTEND_PAYMENT_FAILED,
                                    Placeholder.unparsed("error", response.errorMessage)));
                            return;
                        }
                        OfflinePlayer landlord = Bukkit.getOfflinePlayer(success.landlordId());
                        economy.depositPlayer(landlord, price);
                    }
                    CompletableFuture.supplyAsync(() -> {
                        try {
                            RealtyApi.RenewLeaseholdResult result = logic.renewLeasehold(
                                    regionId, region.world().getUID(), sender.getUniqueId());
                            if (result instanceof RealtyApi.RenewLeaseholdResult.Success) {
                                return logic.getRegionPlaceholders(regionId, region.world().getUID());
                            }
                            return null;
                        } catch (Exception ex) {
                            return null;
                        }
                    }, executorState.dbExec()).thenAcceptAsync(placeholders -> {
                        if (placeholders == null) {
                            if (price > 0) {
                                economy.depositPlayer(sender, price);
                            }
                            sender.sendMessage(messages.messageFor(MessageKeys.EXTEND_UPDATE_FAILED,
                                    Placeholder.unparsed("region", regionId)));
                            return;
                        }
                        signTextApplicator.updateLoadedSigns(region.world(), regionId, RegionState.LEASED, placeholders);
                        sender.sendMessage(messages.messageFor(MessageKeys.EXTEND_SUCCESS,
                                Placeholder.unparsed("region", regionId),
                                Placeholder.unparsed("price", CurrencyFormatter.format(price))));
                    }, executorState.mainThreadExec());
                }
            }
        }, executorState.mainThreadExec());
    }

}
