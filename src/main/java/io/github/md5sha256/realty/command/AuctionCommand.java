package io.github.md5sha256.realty.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.md5sha256.realty.command.util.DurationArgument;
import io.github.md5sha256.realty.command.util.WorldGuardRegion;
import io.github.md5sha256.realty.command.util.WorldGuardRegionArgument;
import io.github.md5sha256.realty.command.util.WorldGuardRegionResolver;
import io.github.md5sha256.realty.database.Database;
import io.github.md5sha256.realty.database.SqlSessionWrapper;
import io.github.md5sha256.realty.database.mapper.SaleContractAuctionMapper;
import io.github.md5sha256.realty.util.ExecutorState;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.apache.ibatis.session.SqlSession;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

/**
 * Handles {@code /realty auction <player> <duration> <paymentduration> <minimumbid> <minimumpricestep> [region]}.
 *
 * <p>Base permission: {@code realty.command.auction}.
 * Auctioning on behalf of another player additionally requires {@code realty.command.auction.others}.</p>
 */
public record AuctionCommand(@NotNull ExecutorState executorState,
                             @NotNull Database database) implements RealtyCommandBean, CustomCommandBean.Single<CommandSourceStack> {

    @Override
    public @NotNull LiteralArgumentBuilder<? extends CommandSourceStack> command() {
        return Commands.literal("auction")
                .requires(source -> source.getSender() instanceof Player player && player.hasPermission("realty.command.auction"))
                .then(Commands.argument("bidDuration", DurationArgument.duration())
                        .then(Commands.argument("paymentDuration", DurationArgument.duration())
                                .then(Commands.argument("minBid", DoubleArgumentType.doubleArg(0))
                                        .then(Commands.argument("minBidStep", DoubleArgumentType.doubleArg(0))
                                                .then(Commands.argument("region", new WorldGuardRegionArgument())
                                                        .executes(this::execute))))));
    }

    private int execute(@NotNull CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Duration bidDuration = ctx.getArgument("bidDuration", Duration.class);
        Duration paymentDuration = ctx.getArgument("paymentDuration", Duration.class);
        double minBid = ctx.getArgument("minBid", Double.class);
        double minBidStep = ctx.getArgument("minBidStep", Double.class);
        WorldGuardRegion region = WorldGuardRegionResolver.resolve(ctx, "region").resolve();
        CompletableFuture.runAsync(() -> {
            try (SqlSessionWrapper wrapper = database.openSession(); SqlSession session = wrapper.session()) {
                SaleContractAuctionMapper auctionMapper = wrapper.saleContractAuctionMapper();
                auctionMapper.createAuction(
                        region.region().getId(),
                        region.world().getUID(),
                        LocalDateTime.now(),
                        bidDuration.toSeconds(),
                        paymentDuration.toSeconds(),
                        minBid,
                        minBidStep
                );
                session.commit();
            }
        }, executorState.dbExec());
        return Command.SINGLE_SUCCESS;
    }

}
