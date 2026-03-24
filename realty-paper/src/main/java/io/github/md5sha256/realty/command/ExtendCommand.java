package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.api.CurrencyFormatter;
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
 * Handles {@code /realty extend <region>}.
 *
 * <p>Permission: {@code realty.command.extend}.</p>
 */
public record ExtendCommand(
        @NotNull ExecutorState executorState,
        @NotNull RealtyLogicImpl logic,
        @NotNull Economy economy,
        @NotNull SignTextApplicator signTextApplicator,
        @NotNull MessageContainer messages
) implements CustomCommandBean.Single {

    @Override
    public @NotNull Command<CommandSourceStack> command(@NotNull Command.Builder<CommandSourceStack> builder) {
        return builder
                .literal("extend")
                .permission("realty.command.extend")
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
        CompletableFuture.supplyAsync(() -> {
            try {
                RealtyLogicImpl.RenewLeaseholdResult result = logic.renewLeasehold(
                        regionId, region.world().getUID(), sender.getUniqueId());
                return switch (result) {
                    case RealtyLogicImpl.RenewLeaseholdResult.Success success -> {
                        Map<String, String> placeholders = logic.getRegionPlaceholders(regionId, region.world().getUID());
                        yield Map.entry(success, placeholders);
                    }
                    case RealtyLogicImpl.RenewLeaseholdResult.NoLeaseholdContract ignored -> {
                        sender.sendMessage(messages.messageFor(MessageKeys.EXTEND_NO_LEASEHOLD_CONTRACT,
                                Placeholder.unparsed("region", regionId)));
                        yield null;
                    }
                    case RealtyLogicImpl.RenewLeaseholdResult.NoExtensionsRemaining ignored -> {
                        sender.sendMessage(messages.messageFor(MessageKeys.EXTEND_NO_EXTENSIONS,
                                Placeholder.unparsed("region", regionId)));
                        yield null;
                    }
                    case RealtyLogicImpl.RenewLeaseholdResult.UpdateFailed ignored -> {
                        sender.sendMessage(messages.messageFor(MessageKeys.EXTEND_UPDATE_FAILED,
                                Placeholder.unparsed("region", regionId)));
                        yield null;
                    }
                };
            } catch (Exception ex) {
                sender.sendMessage(messages.messageFor(MessageKeys.EXTEND_ERROR,
                        Placeholder.unparsed("error", ex.getMessage())));
                return null;
            }
        }, executorState.dbExec()).thenAcceptAsync(entry -> {
            if (entry == null) {
                return;
            }
            RealtyLogicImpl.RenewLeaseholdResult.Success success = entry.getKey();
            Map<String, String> placeholders = entry.getValue();
            double price = success.price();
            double refund = success.refund();
            double cost = Math.max(0, price - refund);
            double balance = economy.getBalance(sender);
            if (balance < cost) {
                sender.sendMessage(messages.messageFor(MessageKeys.EXTEND_INSUFFICIENT_FUNDS,
                        Placeholder.unparsed("balance", CurrencyFormatter.format(balance)),
                        Placeholder.unparsed("price", CurrencyFormatter.format(cost))));
                return;
            }
            if (cost > 0) {
                EconomyResponse response = economy.withdrawPlayer(sender, cost);
                if (!response.transactionSuccess()) {
                    sender.sendMessage(messages.messageFor(MessageKeys.EXTEND_PAYMENT_FAILED,
                            Placeholder.unparsed("error", response.errorMessage)));
                    return;
                }
                OfflinePlayer landlord = Bukkit.getOfflinePlayer(success.landlordId());
                economy.depositPlayer(landlord, cost);
            }
            signTextApplicator.updateLoadedSigns(region.world(), regionId, RegionState.LEASED, placeholders);
            sender.sendMessage(messages.messageFor(MessageKeys.EXTEND_SUCCESS,
                    Placeholder.unparsed("region", regionId),
                    Placeholder.unparsed("price", CurrencyFormatter.format(cost))));
        }, executorState.mainThreadExec());
    }

}
