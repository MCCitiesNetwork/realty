package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.command.util.WorldGuardRegion;
import io.github.md5sha256.realty.command.util.WorldGuardRegionResolver;
import io.github.md5sha256.realty.database.RealtyLogicImpl;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.util.ExecutorState;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * Handles {@code /realty renew <region>}.
 *
 * <p>Permission: {@code realty.command.renew}.</p>
 */
public record RenewCommand(
        @NotNull ExecutorState executorState,
        @NotNull RealtyLogicImpl logic,
        @NotNull Economy economy,
        @NotNull MessageContainer messages
) implements CustomCommandBean.Single {

    @Override
    public @NotNull Command<CommandSourceStack> command(@NotNull CommandManager<CommandSourceStack> manager) {
        return manager.commandBuilder("realty")
                .literal("renew")
                .permission("realty.command.renew")
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
            sender.sendMessage(messages.messageFor("error.no-region"));
            return;
        }
        String regionId = region.region().getId();
        CompletableFuture.supplyAsync(() -> {
            try {
                RealtyLogicImpl.RenewLeaseResult result = logic.renewLease(
                        regionId, region.world().getUID(), sender.getUniqueId());
                return switch (result) {
                    case RealtyLogicImpl.RenewLeaseResult.Success success -> success;
                    case RealtyLogicImpl.RenewLeaseResult.NoLeaseContract ignored -> {
                        sender.sendMessage(messages.messageFor("renew.no-lease-contract",
                                Placeholder.unparsed("region", regionId)));
                        yield null;
                    }
                    case RealtyLogicImpl.RenewLeaseResult.NotTenant ignored -> {
                        sender.sendMessage(messages.messageFor("renew.not-tenant",
                                Placeholder.unparsed("region", regionId)));
                        yield null;
                    }
                    case RealtyLogicImpl.RenewLeaseResult.NoExtensionsRemaining ignored -> {
                        sender.sendMessage(messages.messageFor("renew.no-extensions",
                                Placeholder.unparsed("region", regionId)));
                        yield null;
                    }
                    case RealtyLogicImpl.RenewLeaseResult.UpdateFailed ignored -> {
                        sender.sendMessage(messages.messageFor("renew.update-failed",
                                Placeholder.unparsed("region", regionId)));
                        yield null;
                    }
                };
            } catch (Exception ex) {
                sender.sendMessage(messages.messageFor("renew.error",
                        Placeholder.unparsed("error", ex.getMessage())));
                return null;
            }
        }, executorState.dbExec()).thenAcceptAsync(success -> {
            if (success == null) {
                return;
            }
            double price = success.price();
            double balance = economy.getBalance(sender);
            if (balance < price) {
                sender.sendMessage(messages.messageFor("renew.insufficient-funds",
                        Placeholder.unparsed("balance", String.valueOf(balance)),
                        Placeholder.unparsed("price", String.valueOf(price))));
                return;
            }
            EconomyResponse response = economy.withdrawPlayer(sender, price);
            if (!response.transactionSuccess()) {
                sender.sendMessage(messages.messageFor("renew.payment-failed",
                        Placeholder.unparsed("error", response.errorMessage)));
                return;
            }
            OfflinePlayer landlord = Bukkit.getOfflinePlayer(success.landlordId());
            economy.depositPlayer(landlord, price);
            sender.sendMessage(messages.messageFor("renew.success",
                    Placeholder.unparsed("region", regionId),
                    Placeholder.unparsed("price", String.valueOf(price))));
        }, executorState.mainThreadExec());
    }

}
