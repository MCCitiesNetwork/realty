package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.api.RegionProfileService;
import io.github.md5sha256.realty.api.RegionState;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Handles {@code /realty create lease <price> <period> <maxrenewals> <region>}
 * and {@code /realty create freehold [--price <price>] [--titleholder <name>] [--authority <name>] <region>}.
 *
 * <p>Permissions: {@code realty.command.create.lease} / {@code realty.command.create.freehold}.</p>
 */
public record CreateCommand(@NotNull ExecutorState executorState,
                             @NotNull RealtyLogicImpl logic,
                             @NotNull AtomicReference<Settings> settings,
                             @NotNull RegionProfileService regionProfileService,
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

    private static final CommandFlag<Double> PRICE_FLAG =
            CommandFlag.<CommandSourceStack>builder("price")
                    .withComponent(DoubleParser.doubleParser(0))
                    .build();

    private static final CommandFlag<UUID> LANDLORD_FLAG =
            CommandFlag.<CommandSourceStack>builder("landlord")
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
                        .flag(LANDLORD_FLAG)
                        .required(REGION, WorldGuardRegionParser.worldGuardRegion())
                        .handler(this::executeLease)
                        .build(),
                base.literal("freehold")
                        .permission("realty.command.create.freehold")
                        .flag(PRICE_FLAG)
                        .flag(TITLEHOLDER_FLAG)
                        .flag(AUTHORITY_FLAG)
                        .required(REGION, WorldGuardRegionParser.worldGuardRegion())
                        .handler(this::executeFreehold)
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
        UUID landlord = ctx.flags()
                .getValue(LANDLORD_FLAG, settings.get().defaultLeaseAuthority());
        WorldGuardRegion region = ctx.get(REGION);
        CompletableFuture.supplyAsync(() -> {
            try {
                boolean created = logic.createRental(
                        region.region().getId(), region.world().getUID(),
                        price, period.toSeconds(), maxRenewals, landlord);
                Map<String, String> placeholders = created
                        ? logic.getRegionPlaceholders(region.region().getId(), region.world().getUID())
                        : Map.<String, String>of();
                return Map.entry(created, placeholders);
            } catch (Exception ex) {
                throw new CompletionException(ex);
            }
        }, executorState.dbExec()).thenAcceptAsync(entry -> {
            if (entry.getKey()) {
                regionProfileService.applyFlags(region, RegionState.FOR_LEASE, entry.getValue());
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

    private void executeFreehold(@NotNull CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.sender().getSender();
        if (!(sender instanceof Player)) {
            return;
        }
        Double price = ctx.flags().getValue(PRICE_FLAG, null);
        UUID authority = ctx.flags()
                .getValue(AUTHORITY_FLAG, settings.get().defaultFreeholdAuthority());
        UUID titleholder = ctx.flags()
                .getValue(TITLEHOLDER_FLAG, settings.get().defaultFreeholdTitleholder());
        WorldGuardRegion region = ctx.get(REGION);
        CompletableFuture.supplyAsync(() -> {
            try {
                boolean created = logic.createFreehold(
                        region.region().getId(), region.world().getUID(),
                        price, authority, titleholder);
                Map<String, String> placeholders = created
                        ? logic.getRegionPlaceholders(region.region().getId(), region.world().getUID())
                        : Map.<String, String>of();
                return Map.entry(created, placeholders);
            } catch (Exception ex) {
                throw new CompletionException(ex);
            }
        }, executorState.dbExec()).thenAcceptAsync(entry -> {
            if (entry.getKey()) {
                region.region().getMembers().addPlayer(authority);
                regionProfileService.applyFlags(region,
                        titleholder != null ? RegionState.SOLD : RegionState.FOR_SALE, entry.getValue());
                sender.sendMessage(messages.messageFor("create-freehold.success"));
            } else {
                sender.sendMessage(messages.messageFor("create-freehold.already-registered"));
            }
        }, executorState.mainThreadExec()).exceptionally(ex -> {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            cause.printStackTrace();
            sender.sendMessage(messages.messageFor("create-freehold.error",
                    Placeholder.unparsed("error", cause.getMessage())));
            return null;
        });
    }

}
