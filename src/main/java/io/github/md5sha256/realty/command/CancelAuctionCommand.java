package io.github.md5sha256.realty.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.md5sha256.realty.command.util.WorldGuardRegion;
import io.github.md5sha256.realty.command.util.WorldGuardRegionArgument;
import io.github.md5sha256.realty.command.util.WorldGuardRegionResolver;
import io.github.md5sha256.realty.database.Database;
import io.github.md5sha256.realty.database.SqlSessionWrapper;
import io.github.md5sha256.realty.database.mapper.SaleContractAuctionMapper;
import io.github.md5sha256.realty.util.ExecutorState;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.apache.ibatis.exceptions.PersistenceException;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * Handles {@code /realty cancelauction <region>}.
 *
 * <p>Permission: {@code realty.command.cancelauction}.</p>
 */
public record CancelAuctionCommand(
        @NotNull ExecutorState executorState,
        @NotNull Database database
) implements RealtyCommandBean, CustomCommandBean.Single<CommandSourceStack> {

    @Override
    public @NotNull LiteralArgumentBuilder<? extends CommandSourceStack> command() {
        return Commands.literal("cancelauction")
                .requires(source -> source.getSender() instanceof Player player && player.hasPermission("realty.command.cancelauction"))
                .then(Commands.argument("region", new WorldGuardRegionArgument())
                        .executes(this::execute));
    }

    private int execute(@NotNull CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        WorldGuardRegion region = WorldGuardRegionResolver.resolve(ctx, "region").resolve();
        Player sender = (Player) ctx.getSource().getSender();
        CompletableFuture.runAsync(() -> {
            try (SqlSessionWrapper wrapper = database.openSession()) {
                SaleContractAuctionMapper auctionMapper = wrapper.saleContractAuctionMapper();
                int deleted = auctionMapper.deleteActiveAuctionByRegion(region.region().getId(), region.world().getUID());
                wrapper.session().commit();
                if (deleted == 0) {
                    sender.sendMessage("That region does not have an active auction!");
                    return;
                }
                sender.sendMessage("Auction for region " + region.region().getId() + " has been cancelled.");
            } catch (PersistenceException ex) {
                sender.sendMessage("Failed to cancel auction: " + ex.getMessage());
            }
        }, executorState.dbExec());
        return Command.SINGLE_SUCCESS;
    }

}
