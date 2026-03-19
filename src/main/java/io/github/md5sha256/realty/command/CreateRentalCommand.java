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
import org.apache.ibatis.exceptions.PersistenceException;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Handles {@code /realty createrental <price> <period> <maxrenewals> [--authority <name>] <region>}.
 *
 * <p>Permission: {@code realty.command.createrental}.</p>
 */
public record CreateRentalCommand(@NotNull ExecutorState executorState,
                                  @NotNull RealtyLogicImpl logic,
                                  @NotNull AtomicReference<Settings> settings,
                                  @NotNull MessageContainer messages) implements CustomCommandBean.Single {

    private static final CloudKey<Double> PRICE = CloudKey.of("price", Double.class);
    private static final CloudKey<Duration> PERIOD = CloudKey.of("period", Duration.class);
    private static final CloudKey<Integer> MAX_RENEWALS = CloudKey.of("maxrenewals", Integer.class);
    private static final CloudKey<WorldGuardRegion> REGION = CloudKey.of("region",
            WorldGuardRegion.class);
    private static final CommandFlag<UUID> AUTHORITY_FLAG =
            CommandFlag.<CommandSourceStack>builder("authority")
                    .withComponent(AuthorityParser.authority())
                    .build();

    @Override
    public @NotNull Command<CommandSourceStack> command(@NotNull CommandManager<CommandSourceStack> manager) {
        return manager.commandBuilder("realty")
                .literal("createrental")
                .permission("realty.command.createrental")
                .required(PRICE, DoubleParser.doubleParser(0))
                .required(PERIOD, DurationParser.duration())
                .required(MAX_RENEWALS, IntegerParser.integerParser(-1))
                .flag(AUTHORITY_FLAG)
                .required(REGION, WorldGuardRegionParser.worldGuardRegion())
                .handler(this::execute)
                .build();
    }

    private void execute(@NotNull CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.sender().getSender();
        if (!(sender instanceof Player)) {
            return;
        }
        double price = ctx.get(PRICE);
        Duration period = ctx.get(PERIOD);
        int maxRenewals = ctx.get(MAX_RENEWALS);
        UUID landlord = ctx.flags().getValue(AUTHORITY_FLAG, settings.get().defaultRentAuthority());
        WorldGuardRegion region = ctx.get(REGION);
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
