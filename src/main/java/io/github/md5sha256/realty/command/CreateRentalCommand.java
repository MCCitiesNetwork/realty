package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.command.util.AuthorityParser;
import io.github.md5sha256.realty.command.util.DurationParser;
import io.github.md5sha256.realty.command.util.WorldGuardRegion;
import io.github.md5sha256.realty.command.util.WorldGuardRegionParser;
import io.github.md5sha256.realty.database.RealtyLogicImpl;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.util.ExecutorState;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.apache.ibatis.exceptions.PersistenceException;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.DoubleParser;
import org.incendo.cloud.parser.standard.IntegerParser;
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
                                  @NotNull MessageContainer messages) implements CustomCommandBean.Single {

    @Override
    public @NotNull Command<CommandSourceStack> command(@NotNull CommandManager<CommandSourceStack> manager) {
        return manager.commandBuilder("realty")
                .literal("createrental")
                .permission("realty.command.createrental")
                .required("price", DoubleParser.doubleParser(0))
                .required("period", DurationParser.duration())
                .required("maxrenewals", IntegerParser.integerParser(-1))
                .required("landlord", AuthorityParser.authority())
                .required("region", WorldGuardRegionParser.worldGuardRegion())
                .handler(this::execute)
                .build();
    }

    private void execute(@NotNull CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.sender().getSender();
        if (!(sender instanceof Player)) {
            return;
        }
        double price = ctx.get("price");
        Duration period = ctx.get("period");
        int maxRenewals = ctx.get("maxrenewals");
        UUID landlord = ctx.get("landlord");
        WorldGuardRegion region = ctx.get("region");
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
    }

}
