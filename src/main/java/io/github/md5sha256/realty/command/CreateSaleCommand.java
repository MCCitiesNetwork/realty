package io.github.md5sha256.realty.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.md5sha256.realty.command.util.AuthorityArgument;
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
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Handles {@code /realty createsale <price> <region> [authority]}.
 *
 * <p>Permission: {@code realty.command.createsale}.</p>
 */
public record CreateSaleCommand(@NotNull ExecutorState executorState,
                                @NotNull RealtyLogicImpl logic,
                                @NotNull MessageContainer messages) implements CustomCommandBean.Single<CommandSourceStack> {

    @Override
    public @NotNull LiteralArgumentBuilder<CommandSourceStack> command() {
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
        WorldGuardRegion region = ctx.getArgument("region", WorldGuardRegionResolver.class).resolve();
        CommandSender sender = ctx.getSource().getSender();
        UUID titleHolder = ((Player) sender).getUniqueId();
        CompletableFuture.supplyAsync(() -> {
            try {
                return logic.createSale(
                        region.region().getId(), region.world().getUID(),
                        price, authority, titleHolder);
            } catch (PersistenceException ex) {
                throw new CompletionException(ex);
            }
        }, executorState.dbExec()).thenAcceptAsync(created -> {
            if (created) {
                region.region().getMembers().addPlayer(authority);
                sender.sendMessage(messages.messageFor("create-sale.success"));
            } else {
                sender.sendMessage(messages.messageFor("create-sale.already-registered"));
            }
        }, executorState.mainThreadExec()).exceptionally(ex -> {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            cause.printStackTrace();
            sender.sendMessage(messages.messageFor("create-sale.error",
                    Placeholder.unparsed("error", cause.getMessage())));
            return null;
        });
        return Command.SINGLE_SUCCESS;
    }

}
