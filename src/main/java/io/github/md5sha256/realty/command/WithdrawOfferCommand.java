package io.github.md5sha256.realty.command;

import com.mojang.brigadier.Command;
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
 * Handles {@code /realty withdrawoffer <region>}.
 *
 * <p>Permission: {@code realty.command.withdrawoffer}.</p>
 */
public record WithdrawOfferCommand(
        @NotNull ExecutorState executorState,
        @NotNull RealtyLogicImpl logic
) implements RealtyCommandBean, CustomCommandBean.Single<CommandSourceStack> {

    @Override
    public @NotNull LiteralArgumentBuilder<? extends CommandSourceStack> command() {
        return Commands.literal("withdrawoffer")
                .requires(source -> source.getSender() instanceof Player player && player.hasPermission("realty.command.withdrawoffer"))
                .then(Commands.argument("region", new WorldGuardRegionArgument())
                        .executes(this::execute));
    }

    private int execute(@NotNull CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        WorldGuardRegion region = WorldGuardRegionResolver.resolve(ctx, "region").resolve();
        Player sender = (Player) ctx.getSource().getSender();
        CompletableFuture.runAsync(() -> {
            try {
                int deleted = logic.withdrawOffer(region.region().getId(), region.world().getUID(), sender.getUniqueId());
                if (deleted == 0) {
                    sender.sendMessage("You do not have an offer on region " + region.region().getId() + ".");
                    return;
                }
                sender.sendMessage("Your offer on region " + region.region().getId() + " has been withdrawn.");
            } catch (PersistenceException ex) {
                sender.sendMessage("Failed to withdraw offer: " + ex.getMessage());
            }
        }, executorState.dbExec());
        return Command.SINGLE_SUCCESS;
    }

}
