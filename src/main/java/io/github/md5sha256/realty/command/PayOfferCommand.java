package io.github.md5sha256.realty.command;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import io.github.md5sha256.realty.command.util.WorldGuardRegion;
import io.github.md5sha256.realty.command.util.WorldGuardRegionParser;
import io.github.md5sha256.realty.database.RealtyLogicImpl;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.util.ExecutorState;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.apache.ibatis.exceptions.PersistenceException;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.DoubleParser;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * Handles {@code /realty payoffer <amount> <region>}.
 *
 * <p>Permission: {@code realty.command.payoffer}.</p>
 */
public record PayOfferCommand(
        @NotNull ExecutorState executorState,
        @NotNull RealtyLogicImpl logic,
        @NotNull Economy economy,
        @NotNull MessageContainer messages
) implements CustomCommandBean.Single {

    @Override
    public @NotNull Command<CommandSourceStack> command(@NotNull CommandManager<CommandSourceStack> manager) {
        return manager.commandBuilder("realty")
                .literal("payoffer")
                .permission("realty.command.payoffer")
                .required("amount", DoubleParser.doubleParser(0, Double.MAX_VALUE))
                .required("region", WorldGuardRegionParser.worldGuardRegion())
                .handler(this::execute)
                .build();
    }

    private void execute(@NotNull CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.sender().getSender() instanceof Player sender)) {
            ctx.sender().getSender().sendMessage(messages.messageFor("common.players-only"));
            return;
        }
        double amount = ctx.get("amount");
        WorldGuardRegion region = ctx.get("region");
        String regionId = region.region().getId();
        // Balance check on main thread
        double balance = economy.getBalance(sender);
        if (balance < amount) {
            sender.sendMessage(messages.messageFor("pay-offer.insufficient-funds",
                    Placeholder.unparsed("balance", String.valueOf(balance))));
            return;
        }
        EconomyResponse response = economy.withdrawPlayer(sender, amount);
        if (!response.transactionSuccess()) {
            sender.sendMessage(messages.messageFor("pay-offer.payment-failed",
                    Placeholder.unparsed("error", response.errorMessage)));
            return;
        }
        // DB logic on async thread
        CompletableFuture.supplyAsync(() -> {
            try {
                RealtyLogicImpl.PayOfferResult result = logic.payOffer(
                        regionId, region.world().getUID(),
                        sender.getUniqueId(), amount);
                return switch (result) {
                    case RealtyLogicImpl.PayOfferResult.Success success -> {
                        sender.sendMessage(messages.messageFor("pay-offer.success",
                                Placeholder.unparsed("amount", String.valueOf(amount)),
                                Placeholder.unparsed("region", regionId),
                                Placeholder.unparsed("total", String.valueOf(success.newTotal())),
                                Placeholder.unparsed("remaining", String.valueOf(success.remaining()))));
                        yield null;
                    }
                    case RealtyLogicImpl.PayOfferResult.FullyPaid fullyPaid -> {
                        sender.sendMessage(messages.messageFor("pay-offer.fully-paid",
                                Placeholder.unparsed("amount", String.valueOf(amount)),
                                Placeholder.unparsed("region", regionId)));
                        yield fullyPaid;
                    }
                    case RealtyLogicImpl.PayOfferResult.NoPaymentRecord ignored -> {
                        sender.sendMessage(messages.messageFor("pay-offer.no-payment-record",
                                Placeholder.unparsed("region", regionId)));
                        yield null;
                    }
                    case RealtyLogicImpl.PayOfferResult.ExceedsAmountOwed exceeds -> {
                        sender.sendMessage(messages.messageFor("pay-offer.exceeds-owed",
                                Placeholder.unparsed("amount", String.valueOf(amount)),
                                Placeholder.unparsed("owed", String.valueOf(exceeds.amountOwed())),
                                Placeholder.unparsed("region", regionId)));
                        yield null;
                    }
                };
            } catch (PersistenceException ex) {
                sender.sendMessage(messages.messageFor("pay-offer.error",
                        Placeholder.unparsed("error", ex.getMessage())));
                return null;
            }
        }, executorState.dbExec()).thenAcceptAsync(fullyPaid -> {
            if (fullyPaid == null) {
                economy.depositPlayer(sender, amount);
            } else {
                OfflinePlayer authority = Bukkit.getOfflinePlayer(fullyPaid.authorityId());
                economy.depositPlayer(authority, amount);
                RegionManager regionManager = WorldGuard.getInstance()
                        .getPlatform()
                        .getRegionContainer()
                        .get(BukkitAdapter.adapt(region.world()));
                if (regionManager == null) {
                    sender.sendMessage(messages.messageFor("pay-offer.transfer-failed"));
                    return;
                }
                ProtectedRegion protectedRegion = region.region();
                protectedRegion.getOwners().clear();
                protectedRegion.getOwners().addPlayer(sender.getUniqueId());
                protectedRegion.getMembers().clear();
                sender.sendMessage(messages.messageFor("pay-offer.transfer-success",
                        Placeholder.unparsed("region", regionId)));
            }
        }, executorState.mainThreadExec());
    }

}
