package io.github.md5sha256.realty.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.managers.storage.StorageException;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import io.github.md5sha256.realty.command.util.WorldGuardRegion;
import io.github.md5sha256.realty.command.util.WorldGuardRegionArgument;
import io.github.md5sha256.realty.command.util.WorldGuardRegionResolver;
import io.github.md5sha256.realty.database.RealtyLogicImpl;
import io.github.md5sha256.realty.util.ExecutorState;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.apache.ibatis.exceptions.PersistenceException;
import org.bukkit.entity.Player;
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
        @NotNull Economy economy
) implements RealtyCommandBean, CustomCommandBean.Single<CommandSourceStack> {

    @Override
    public @NotNull LiteralArgumentBuilder<? extends CommandSourceStack> command() {
        return Commands.literal("payoffer")
                .requires(source -> source.getSender() instanceof Player player && player.hasPermission(
                        "realty.command.payoffer"))
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
            sender.sendMessage("You do not have enough funds. Your balance: " + balance + ".");
            return Command.SINGLE_SUCCESS;
        }
        EconomyResponse response = economy.withdrawPlayer(sender, amount);
        if (!response.transactionSuccess()) {
            sender.sendMessage("Payment failed: " + response.errorMessage);
            return Command.SINGLE_SUCCESS;
        }
        // DB logic on async thread
        CompletableFuture.supplyAsync(() -> {
            try {
                RealtyLogicImpl.PayOfferResult result = logic.payOffer(
                        regionId, region.world().getUID(),
                        sender.getUniqueId(), amount);
                return switch (result) {
                    case RealtyLogicImpl.PayOfferResult.Success success -> {
                        sender.sendMessage("Payment of " + amount + " applied to region " + regionId
                                + ". Total paid: " + success.newTotal() + ". Remaining: " + success.remaining() + ".");
                        yield false;
                    }
                    case RealtyLogicImpl.PayOfferResult.FullyPaid ignored -> {
                        sender.sendMessage("Payment of " + amount + " applied to region " + regionId
                                + ". Offer fully paid! Region ownership will be transferred to you.");
                        yield true;
                    }
                    case RealtyLogicImpl.PayOfferResult.NoPaymentRecord ignored -> {
                        sender.sendMessage("You do not have an accepted offer on region " + regionId + ". Payment refunded.");
                        yield false;
                    }
                    case RealtyLogicImpl.PayOfferResult.ExceedsAmountOwed exceeds -> {
                        sender.sendMessage("Payment of " + amount + " exceeds the remaining amount owed ("
                                + exceeds.amountOwed() + ") on region " + regionId + ". Payment refunded.");
                        yield false;
                    }
                };
            } catch (PersistenceException ex) {
                sender.sendMessage("Failed to process payment: " + ex.getMessage() + ". Payment refunded.");
                return false;
            }
        }, executorState.dbExec()).thenAcceptAsync(shouldTransfer -> {
            if (Boolean.FALSE.equals(shouldTransfer)) {
                economy.depositPlayer(sender, amount);
            } else {
                RegionManager regionManager = WorldGuard.getInstance()
                        .getPlatform()
                        .getRegionContainer()
                        .get(BukkitAdapter.adapt(region.world()));
                if (regionManager == null) {
                    sender.sendMessage("Failed to transfer region: WorldGuard region manager not available.");
                    return;
                }
                ProtectedRegion protectedRegion = region.region();
                protectedRegion.getOwners().clear();
                protectedRegion.getOwners().addPlayer(sender.getUniqueId());
                protectedRegion.getMembers().clear();
                try {
                    regionManager.save();
                    sender.sendMessage("Region " + regionId + " ownership has been transferred to you.");
                } catch (StorageException ex) {
                    sender.sendMessage("Region paid but failed to save WorldGuard changes: " + ex.getMessage());
                }
            }
        }, executorState.mainThreadExec());
        return Command.SINGLE_SUCCESS;
    }

}
