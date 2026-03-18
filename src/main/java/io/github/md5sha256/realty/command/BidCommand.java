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
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.util.ExecutorState;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
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
        @NotNull RealtyLogicImpl logic,
        @NotNull MessageContainer messages
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
        String regionId = region.region().getId();
        CompletableFuture.runAsync(() -> {
            try {
                RealtyLogicImpl.BidResult result = logic.performBid(
                        regionId, region.world().getUID(),
                        sender.getUniqueId(), bidAmount);
                switch (result) {
                    case RealtyLogicImpl.BidResult.Success ignored ->
                            sender.sendMessage(messages.messageFor("bid.success",
                                    Placeholder.unparsed("amount", String.valueOf(bidAmount)),
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyLogicImpl.BidResult.NoAuction ignored ->
                            sender.sendMessage(messages.messageFor("bid.no-auction"));
                    case RealtyLogicImpl.BidResult.BidTooLowMinimum r ->
                            sender.sendMessage(messages.messageFor("bid.too-low-minimum",
                                    Placeholder.unparsed("amount", String.valueOf(r.minBid()))));
                    case RealtyLogicImpl.BidResult.BidTooLowCurrent r ->
                            sender.sendMessage(messages.messageFor("bid.too-low-current",
                                    Placeholder.unparsed("amount", String.valueOf(r.currentHighest()))));
                }
            } catch (PersistenceException ex) {
                sender.sendMessage(messages.messageFor("bid.error",
                        Placeholder.unparsed("error", ex.getMessage())));
            }
        }, executorState.dbExec());
        return Command.SINGLE_SUCCESS;
    }

}
