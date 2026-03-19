package io.github.md5sha256.realty.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.md5sha256.realty.command.util.AuthorityArgument;
import io.github.md5sha256.realty.command.util.DurationArgument;
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

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Handles {@code /realty createrental <price> <period> <maxrenewals> <landlord> <region>}.
 *
 * <p>Permission: {@code realty.command.createrental}.</p>
 */
public record CreateRentalCommand(@NotNull ExecutorState executorState,
                                  @NotNull RealtyLogicImpl logic,
                                  @NotNull MessageContainer messages) implements CustomCommandBean.Single<CommandSourceStack> {

    @Override
    public @NotNull LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("createrental")
                .requires(source -> source.getSender() instanceof Player player && player.hasPermission("realty.command.createrental"))
                .then(Commands.argument("price", DoubleArgumentType.doubleArg(0))
                        .then(Commands.argument("period", DurationArgument.duration())
                                .then(Commands.argument("maxrenewals", IntegerArgumentType.integer(-1))
                                        .then(Commands.argument("landlord", new AuthorityArgument())
                                                .then(Commands.argument("region", new WorldGuardRegionArgument())
                                                        .executes(this::execute))))));
    }

    private int execute(@NotNull CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        double price = DoubleArgumentType.getDouble(ctx, "price");
        Duration period = ctx.getArgument("period", Duration.class);
        int maxRenewals = IntegerArgumentType.getInteger(ctx, "maxrenewals");
        UUID landlord = ctx.getArgument("landlord", UUID.class);
        WorldGuardRegion region = ctx.getArgument("region", WorldGuardRegionResolver.class).resolve();
        CommandSender sender = ctx.getSource().getSender();
        CompletableFuture.supplyAsync(() -> {
            try {
                return logic.createRental(
                        region.region().getId(), region.world().getUID(),
                        price, period.toSeconds(), maxRenewals, landlord);
            } catch (PersistenceException ex) {
                throw new CompletionException(ex);
            }
        }, executorState.dbExec()).thenAcceptAsync(created -> {
            if (created) {
                region.region().getMembers().addPlayer(landlord);
                sender.sendMessage(messages.messageFor("create-rental.success"));
            } else {
                sender.sendMessage(messages.messageFor("create-rental.already-registered"));
            }
        }, executorState.mainThreadExec()).exceptionally(ex -> {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            cause.printStackTrace();
            sender.sendMessage(messages.messageFor("create-rental.error",
                    Placeholder.unparsed("error", cause.getMessage())));
            return null;
        });
        return Command.SINGLE_SUCCESS;
    }

}
