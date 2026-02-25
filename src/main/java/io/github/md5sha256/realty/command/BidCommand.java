package io.github.md5sha256.realty.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.github.md5sha256.realty.command.util.WorldGuardRegion;
import io.github.md5sha256.realty.command.util.WorldGuardRegionArgument;
import io.github.md5sha256.realty.database.Database;
import io.github.md5sha256.realty.database.SqlSessionWrapper;
import io.github.md5sha256.realty.database.entity.SaleContractAuctionEntity;
import io.github.md5sha256.realty.database.entity.SaleContractBid;
import io.github.md5sha256.realty.database.mapper.SaleContractAuctionMapper;
import io.github.md5sha256.realty.database.mapper.SaleContractBidMapper;
import io.github.md5sha256.realty.util.ExecutorState;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.apache.ibatis.exceptions.PersistenceException;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

/**
 * Handles {@code /realty bid <price> <region>}.
 *
 * <p>Permission: {@code realty.command.bid}.</p>
 */
public record BidCommand(
        @NotNull ExecutorState executorState,
        @NotNull Database database
) implements RealtyCommandBean, CustomCommandBean.Single<CommandSourceStack> {

    @Override
    public @NotNull LiteralArgumentBuilder<? extends CommandSourceStack> command() {
        return Commands.literal("bid")
                .requires(source -> source.getSender() instanceof Player player && player.hasPermission("realty.command.bid"))
                .then(Commands.argument("bid", DoubleArgumentType.doubleArg(0))
                        .then(Commands.argument("region", new WorldGuardRegionArgument()))
                        .executes(this::execute));
    }

    private int execute(@NotNull CommandContext<CommandSourceStack> ctx) {
        double bidAmount = ctx.getArgument("bid", Double.class);
        WorldGuardRegion region = ctx.getArgument("region", WorldGuardRegion.class);
        Player sender = (Player) ctx.getSource().getSender();
        CompletableFuture.runAsync(() -> {
            try (SqlSessionWrapper wrapper = database.openSession()) {
                SaleContractBidMapper bidMapper = wrapper.saleContractBidMapper();
                SaleContractAuctionMapper auctionMapper = wrapper.saleContractAuctionMapper();
                SaleContractAuctionEntity auction = auctionMapper.selectByRegion(region.region().getId(), region.world().getUID());
                if (auction == null) {
                    sender.sendMessage("That region does not have an active auction!");
                    return;
                }
                if (bidAmount < auction.minBid()) {
                    sender.sendMessage("Bid too low, the minimum bid is " + auction.minBid());
                    return;
                }
                SaleContractBid bid = bidMapper.selectHighestBid(region.region().getId(), region.world().getUID());
                if (bid != null && bidAmount < bid.bidAmount()) {
                    sender.sendMessage("Bid too low, the next highest bid is " + bid.bidAmount());
                    return;
                }
                bidMapper.performContractBid(new SaleContractBid(auction.saleContractAuctionId(), sender.getUniqueId(), bidAmount, LocalDateTime.now()));
                wrapper.session().commit();
            } catch (PersistenceException ex) {
                sender.sendMessage("Failed to perform bid: " + ex.getMessage());
            }
        }, executorState.dbExec());
        return Command.SINGLE_SUCCESS;
    }

}
