package io.github.md5sha256.realty.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.md5sha256.realty.command.util.AuthorityArgument;
import io.github.md5sha256.realty.command.util.WorldGuardRegion;
import io.github.md5sha256.realty.command.util.WorldGuardRegionArgument;
import io.github.md5sha256.realty.database.RealtyLogicImpl;
import io.github.md5sha256.realty.util.ExecutorState;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.apache.ibatis.exceptions.PersistenceException;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Handles {@code /realty createsale <price> <region> [authority]}.
 *
 * <p>Permission: {@code realty.command.createsale}.</p>
 */
public record CreateSaleCommand(@NotNull ExecutorState executorState,
                                @NotNull RealtyLogicImpl logic) implements RealtyCommandBean, CustomCommandBean.Single<CommandSourceStack> {

    @Override
    public @NotNull LiteralArgumentBuilder<? extends CommandSourceStack> command() {
        return Commands.literal("createsale")
                .requires(source -> source.getSender() instanceof Player player && player.hasPermission("realty.command.createsale"))
                .then(Commands.argument("price", DoubleArgumentType.doubleArg(0))
                        .then(Commands.argument("authority", new AuthorityArgument())
                                .then(Commands.argument("region", new WorldGuardRegionArgument())
                                        .executes(this::execute))));
    }

    private int execute(@NotNull CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        double price = DoubleArgumentType.getDouble(ctx, "price");
        UUID authority = ctx.getArgument("authority", UUID.class);
        WorldGuardRegion region = ctx.getArgument("region", WorldGuardRegion.class);
        CommandSender sender = ctx.getSource().getSender();
        UUID titleHolder = ((Player) sender).getUniqueId();
        CompletableFuture.runAsync(() -> {
            try {
                boolean created = logic.createSale(
                        region.region().getId(), region.world().getUID(),
                        price, authority, titleHolder);
                if (created) {
                    sender.sendMessage("Sale region created successfully!");
                } else {
                    sender.sendMessage("Region already registered!");
                }
            } catch (PersistenceException ex) {
                ex.printStackTrace();
                sender.sendMessage("Failed to create sale region: " + ex.getMessage());
            }
        }, executorState.dbExec());
        return Command.SINGLE_SUCCESS;
    }

}
