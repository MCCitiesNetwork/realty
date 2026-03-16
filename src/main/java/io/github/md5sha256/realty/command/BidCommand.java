package io.github.md5sha256.realty.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.md5sha256.realty.command.util.WorldGuardRegion;
import io.github.md5sha256.realty.command.util.WorldGuardRegionArgument;
import io.github.md5sha256.realty.command.util.WorldGuardRegionResolver;
import io.github.md5sha256.realty.database.RealtyLogicImpl;
import io.github.md5sha256.realty.util.ExecutorState;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.apache.ibatis.exceptions.PersistenceException;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * Handles {@code /realty bid <price> <region>}.
 *
 * <p>Permission: {@code realty.command.bid}.</p>
 */
public record BidCommand(
        @NotNull ExecutorState executorState,
        @NotNull RealtyLogicImpl logic
) implements RealtyCommandBean, CustomCommandBean.Single<CommandSourceStack> {

    @Override
    public @NotNull LiteralArgumentBuilder<? extends CommandSourceStack> command() {
        return Commands.literal("bid")
                .requires(source -> source.getSender() instanceof Player player && player.hasPermission("realty.command.bid"))
                .then(Commands.argument("bid", DoubleArgumentType.doubleArg(0))
                        .then(Commands.argument("region", new WorldGuardRegionArgument())
                                .executes(this::execute)));
    }

    private int execute(@NotNull CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        double bidAmount = ctx.getArgument("bid", Double.class);
        WorldGuardRegion region = WorldGuardRegionResolver.resolve(ctx, "region").resolve();
        Player sender = (Player) ctx.getSource().getSender();
        CompletableFuture.runAsync(() -> {
            try {
                RealtyLogicImpl.BidResult result = logic.performBid(
                        region.region().getId(), region.world().getUID(),
                        sender.getUniqueId(), bidAmount);
                switch (result) {
                    case RealtyLogicImpl.BidResult.Success ignored -> {}
                    case RealtyLogicImpl.BidResult.NoAuction ignored ->
                            sender.sendMessage("That region does not have an active auction!");
                    case RealtyLogicImpl.BidResult.BidTooLowMinimum r ->
                            sender.sendMessage("Bid too low, the minimum bid is " + r.minBid());
                    case RealtyLogicImpl.BidResult.BidTooLowCurrent r ->
                            sender.sendMessage("Bid too low, the next highest bid is " + r.currentHighest());
                }
            } catch (PersistenceException ex) {
                sender.sendMessage("Failed to perform bid: " + ex.getMessage());
            }
        }, executorState.dbExec());
        return Command.SINGLE_SUCCESS;
    }

}
