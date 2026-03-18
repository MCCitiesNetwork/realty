package io.github.md5sha256.realty.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import io.github.md5sha256.realty.command.util.WorldGuardRegion;
import io.github.md5sha256.realty.command.util.WorldGuardRegionArgument;
import io.github.md5sha256.realty.command.util.WorldGuardRegionResolver;
import io.github.md5sha256.realty.database.RealtyLogicImpl;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.util.ExecutorState;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.apache.ibatis.exceptions.PersistenceException;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * Handles {@code /realty paybid <amount> <region>}.
 *
 * <p>Permission: {@code realty.command.paybid}.</p>
 */
public record PayBidCommand(
        @NotNull ExecutorState executorState,
        @NotNull RealtyLogicImpl logic,
        @NotNull Economy economy,
        @NotNull MessageContainer messages
) implements RealtyCommandBean, CustomCommandBean.Single<CommandSourceStack> {

    @Override
    public @NotNull LiteralArgumentBuilder<? extends CommandSourceStack> command() {
        return Commands.literal("paybid")
                .requires(source -> source.getSender() instanceof Player player && player.hasPermission(
                        "realty.command.paybid"))
                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0, Double.MAX_VALUE))
                        .then(Commands.argument("region", new WorldGuardRegionArgument())
                                .executes(this::execute)));
    }

    private int execute(@NotNull CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        double amount = DoubleArgumentType.getDouble(ctx, "amount");
        WorldGuardRegion region = WorldGuardRegionResolver.resolve(ctx, "region").resolve();
        Player sender = (Player) ctx.getSource().getSender();
        String regionId = region.region().getId();
        // Balance check on main thread
        double balance = economy.getBalance(sender);
        if (balance < amount) {
            sender.sendMessage(messages.messageFor("pay-bid.insufficient-funds",
                    Placeholder.unparsed("balance", String.valueOf(balance))));
            return Command.SINGLE_SUCCESS;
        }
        EconomyResponse response = economy.withdrawPlayer(sender, amount);
        if (!response.transactionSuccess()) {
            sender.sendMessage(messages.messageFor("pay-bid.payment-failed",
                    Placeholder.unparsed("error", response.errorMessage)));
            return Command.SINGLE_SUCCESS;
        }
        // DB logic on async thread
        CompletableFuture.supplyAsync(() -> {
            try {
                RealtyLogicImpl.PayBidResult result = logic.payBid(
                        regionId, region.world().getUID(),
                        sender.getUniqueId(), amount);
                return switch (result) {
                    case RealtyLogicImpl.PayBidResult.Success success -> {
                        sender.sendMessage(messages.messageFor("pay-bid.success",
                                Placeholder.unparsed("amount", String.valueOf(amount)),
                                Placeholder.unparsed("region", regionId),
                                Placeholder.unparsed("total", String.valueOf(success.newTotal())),
                                Placeholder.unparsed("remaining", String.valueOf(success.remaining()))));
                        yield null;
                    }
                    case RealtyLogicImpl.PayBidResult.FullyPaid fullyPaid -> {
                        sender.sendMessage(messages.messageFor("pay-bid.fully-paid",
                                Placeholder.unparsed("amount", String.valueOf(amount)),
                                Placeholder.unparsed("region", regionId)));
                        yield fullyPaid;
                    }
                    case RealtyLogicImpl.PayBidResult.NoPaymentRecord ignored -> {
                        sender.sendMessage(messages.messageFor("pay-bid.no-payment-record",
                                Placeholder.unparsed("region", regionId)));
                        yield null;
                    }
                    case RealtyLogicImpl.PayBidResult.PaymentExpired ignored -> {
                        sender.sendMessage(messages.messageFor("pay-bid.payment-expired",
                                Placeholder.unparsed("region", regionId)));
                        yield null;
                    }
                    case RealtyLogicImpl.PayBidResult.ExceedsAmountOwed exceeds -> {
                        sender.sendMessage(messages.messageFor("pay-bid.exceeds-owed",
                                Placeholder.unparsed("amount", String.valueOf(amount)),
                                Placeholder.unparsed("owed", String.valueOf(exceeds.amountOwed())),
                                Placeholder.unparsed("region", regionId)));
                        yield null;
                    }
                };
            } catch (PersistenceException ex) {
                sender.sendMessage(messages.messageFor("pay-bid.error",
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
                    sender.sendMessage(messages.messageFor("pay-bid.transfer-failed"));
                    return;
                }
                ProtectedRegion protectedRegion = region.region();
                protectedRegion.getOwners().clear();
                protectedRegion.getOwners().addPlayer(sender.getUniqueId());
                protectedRegion.getMembers().clear();
                sender.sendMessage(messages.messageFor("pay-bid.transfer-success",
                        Placeholder.unparsed("region", regionId)));
            }
        }, executorState.mainThreadExec());
        return Command.SINGLE_SUCCESS;
    }

}
