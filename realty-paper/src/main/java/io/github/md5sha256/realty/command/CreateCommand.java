package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.command.util.AuthorityParser;
import io.github.md5sha256.realty.command.util.DurationParser;
import io.github.md5sha256.realty.command.util.WorldGuardRegion;
import io.github.md5sha256.realty.command.util.WorldGuardRegionParser;
import io.github.md5sha256.realty.database.RealtyLogicImpl;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.settings.Settings;
import io.github.md5sha256.realty.util.ExecutorState;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.key.CloudKey;
import org.incendo.cloud.parser.flag.CommandFlag;
import org.incendo.cloud.parser.standard.DoubleParser;
import org.incendo.cloud.parser.standard.IntegerParser;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Handles {@code /realty create lease <price> <period> <maxrenewals> <region>}
 * and {@code /realty create sale <price> [--titleholder <name>] [--authority <name>] <region>}.
 *
 * <p>Permissions: {@code realty.command.create.lease} / {@code realty.command.create.sale}.</p>
 */
public record CreateCommand(@NotNull ExecutorState executorState,
                             @NotNull RealtyLogicImpl logic,
                             @NotNull AtomicReference<Settings> settings,
                             @NotNull MessageContainer messages) implements CustomCommandBean {

    private static final CloudKey<Double> PRICE = CloudKey.of("price", Double.class);
    private static final CloudKey<Duration> PERIOD = CloudKey.of("period", Duration.class);
    private static final CloudKey<Integer> MAX_RENEWALS = CloudKey.of("maxrenewals", Integer.class);
    private static final CloudKey<WorldGuardRegion> REGION = CloudKey.of("region",
            WorldGuardRegion.class);
    private static final CommandFlag<UUID> AUTHORITY_FLAG =
            CommandFlag.<CommandSourceStack>builder("authority")
                    .withComponent(AuthorityParser.authority())
                    .build();

    private static final CommandFlag<UUID> TITLEHOLDER_FLAG =
            CommandFlag.<CommandSourceStack>builder("titleholder")
                    .withComponent(AuthorityParser.authority())
                    .build();


    @Override
    public @NotNull List<Command<CommandSourceStack>> commands(@NotNull CommandManager<CommandSourceStack> manager) {
        var base = manager.commandBuilder("realty")
                .literal("create");
        return List.of(
                base.literal("lease")
                        .permission("realty.command.create.lease")
                        .required(PRICE, DoubleParser.doubleParser(0))
                        .required(PERIOD, DurationParser.duration())
                        .required(MAX_RENEWALS, IntegerParser.integerParser(-1))
                        .required(REGION, WorldGuardRegionParser.worldGuardRegion())
                        .handler(this::executeLease)
                        .build(),
                base.literal("sale")
                        .permission("realty.command.create.sale")
                        .required(PRICE, DoubleParser.doubleParser(0))
                        .flag(TITLEHOLDER_FLAG)
                        .flag(AUTHORITY_FLAG)
                        .required(REGION, WorldGuardRegionParser.worldGuardRegion())
                        .handler(this::executeSale)
                        .build()
        );
    }

    private void executeLease(@NotNull CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.sender().getSender();
        if (!(sender instanceof Player)) {
            return;
        }
        double price = ctx.get(PRICE);
        Duration period = ctx.get(PERIOD);
        int maxRenewals = ctx.get(MAX_RENEWALS);
        WorldGuardRegion region = ctx.get(REGION);
        CompletableFuture.supplyAsync(() -> {
            try {
                return logic.createRental(
                        region.region().getId(), region.world().getUID(),
                        price, period.toSeconds(), maxRenewals, null);
            } catch (Exception ex) {
                throw new CompletionException(ex);
            }
        }, executorState.dbExec()).thenAcceptAsync(created -> {
            if (created) {
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

    private void executeSale(@NotNull CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.sender().getSender();
        if (!(sender instanceof Player)) {
            return;
        }
        double price = ctx.get(PRICE);
        UUID authority = ctx.flags()
                .getValue(AUTHORITY_FLAG, settings.get().defaultSaleAuthority());
        UUID titleholder = ctx.flags()
                .getValue(TITLEHOLDER_FLAG, settings.get().defaultSaleTitleholder());
        WorldGuardRegion region = ctx.get(REGION);
        CompletableFuture.supplyAsync(() -> {
            try {
                return logic.createSale(
                        region.region().getId(), region.world().getUID(),
                        price, authority, titleholder);
            } catch (Exception ex) {
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
    }

}
