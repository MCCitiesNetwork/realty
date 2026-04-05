package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.api.RealtyPaperApi;
import io.github.md5sha256.realty.command.util.AuthorityParser;
import io.github.md5sha256.realty.command.util.DurationParser;
import io.github.md5sha256.realty.command.util.ParseBounds;
import io.github.md5sha256.realty.api.WorldGuardRegion;
import io.github.md5sha256.realty.command.util.WorldGuardRegionResolver;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.localisation.MessageKeys;
import io.github.md5sha256.realty.settings.Settings;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.key.CloudKey;
import org.incendo.cloud.paper.util.sender.Source;
import org.incendo.cloud.parser.flag.CommandFlag;
import org.incendo.cloud.parser.standard.DoubleParser;
import org.incendo.cloud.parser.standard.IntegerParser;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Handles {@code /realty register leasehold <price> <period> <maxextensions> <region>}
 * and {@code /realty register freehold [--price <price>] [--titleholder <name>] [--authority <name>] <region>}.
 *
 * <p>Permissions: {@code realty.command.register.leasehold} / {@code realty.command.register.freehold}.</p>
 */
public record RegisterCommand(@NotNull RealtyPaperApi api,
                              @NotNull AtomicReference<Settings> settings,
                              @NotNull MessageContainer messages) implements CustomCommandBean {

    private static final CloudKey<Double> PRICE = CloudKey.of("price", Double.class);
    private static final CloudKey<Duration> PERIOD = CloudKey.of("period", Duration.class);
    private static final CloudKey<Integer> MAX_EXTENSIONS = CloudKey.of("maxrenewals", Integer.class);
    private static final CommandFlag<UUID> AUTHORITY_FLAG =
            CommandFlag.<Source>builder("authority")
                    .withComponent(AuthorityParser.authority())
                    .build();

    private static final CommandFlag<UUID> TITLEHOLDER_FLAG =
            CommandFlag.<Source>builder("titleholder")
                    .withComponent(AuthorityParser.authority())
                    .build();

    private static final CommandFlag<Double> PRICE_FLAG =
            CommandFlag.<Source>builder("price")
                    .withComponent(DoubleParser.doubleParser(ParseBounds.MIN_STRICTLY_POSITIVE,
                            Double.MAX_VALUE))
                    .build();

    private static final CommandFlag<UUID> LANDLORD_FLAG =
            CommandFlag.<Source>builder("landlord")
                    .withComponent(AuthorityParser.authority())
                    .build();


    @Override
    public @NotNull List<Command<? extends Source>> commands(@NotNull Command.Builder<Source> builder) {
        var base = builder
                .literal("register");
        return List.of(
                base.literal("leasehold")
                        .permission("realty.command.register.leasehold")
                        .required(PRICE, DoubleParser.doubleParser(ParseBounds.MIN_STRICTLY_POSITIVE,
                                Double.MAX_VALUE))
                        .required(PERIOD, DurationParser.duration())
                        .required(MAX_EXTENSIONS, IntegerParser.integerParser(-1))
                        .flag(LANDLORD_FLAG)
                        .optional("region", WorldGuardRegionResolver.worldGuardRegionResolver())
                        .handler(this::executeLeasehold)
                        .build(),
                base.literal("freehold")
                        .permission("realty.command.register.freehold")
                        .flag(PRICE_FLAG)
                        .flag(TITLEHOLDER_FLAG)
                        .flag(AUTHORITY_FLAG)
                        .optional("region", WorldGuardRegionResolver.worldGuardRegionResolver())
                        .handler(this::executeFreehold)
                        .build()
        );
    }

    private void executeLeasehold(@NotNull CommandContext<Source> ctx) {
        CommandSender sender = ctx.sender().source();
        double price = ctx.get(PRICE);
        Duration period = ctx.get(PERIOD);
        int maxExtensions = ctx.get(MAX_EXTENSIONS);
        UUID landlord = ctx.flags()
                .getValue(LANDLORD_FLAG, settings.get().defaultLeaseholdAuthority());
        WorldGuardRegion region = ctx.<WorldGuardRegion>optional("region")
                .orElseGet(() -> sender instanceof Player player
                        ? WorldGuardRegionResolver.resolveAtLocation(player.getLocation()) : null);
        if (region == null) {
            sender.sendMessage(messages.messageFor(MessageKeys.ERROR_NO_REGION));
            return;
        }
        api.registerLeasehold(region, price, period.toSeconds(), maxExtensions, landlord)
                .thenAccept(result -> {
                    switch (result) {
                        case RealtyPaperApi.CreateLeaseholdResult.Success ignored ->
                                sender.sendMessage(messages.messageFor(MessageKeys.REGISTER_RENTAL_SUCCESS));
                        case RealtyPaperApi.CreateLeaseholdResult.AlreadyRegistered ignored ->
                                sender.sendMessage(messages.messageFor(MessageKeys.REGISTER_RENTAL_ALREADY_REGISTERED));
                        case RealtyPaperApi.CreateLeaseholdResult.Error error ->
                                sender.sendMessage(messages.messageFor(MessageKeys.REGISTER_RENTAL_ERROR,
                                        Placeholder.unparsed("error", error.message())));
                    }
                }).exceptionally(ex -> {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    cause.printStackTrace();
                    sender.sendMessage(messages.messageFor(MessageKeys.REGISTER_RENTAL_ERROR,
                            Placeholder.unparsed("error", cause.getMessage())));
                    return null;
                });
    }

    private void executeFreehold(@NotNull CommandContext<Source> ctx) {
        CommandSender sender = ctx.sender().source();
        Double price = ctx.flags().getValue(PRICE_FLAG, null);
        UUID authority = ctx.flags()
                .getValue(AUTHORITY_FLAG, settings.get().defaultFreeholdAuthority());
        UUID titleholder = ctx.flags()
                .getValue(TITLEHOLDER_FLAG, settings.get().defaultFreeholdTitleholder());
        WorldGuardRegion region = ctx.<WorldGuardRegion>optional("region")
                .orElseGet(() -> sender instanceof Player player
                        ? WorldGuardRegionResolver.resolveAtLocation(player.getLocation()) : null);
        if (region == null) {
            sender.sendMessage(messages.messageFor(MessageKeys.ERROR_NO_REGION));
            return;
        }
        api.registerFreehold(region, price, authority, titleholder)
                .thenAccept(result -> {
                    switch (result) {
                        case RealtyPaperApi.CreateFreeholdResult.Success ignored ->
                                sender.sendMessage(messages.messageFor(MessageKeys.REGISTER_FREEHOLD_SUCCESS));
                        case RealtyPaperApi.CreateFreeholdResult.AlreadyRegistered ignored ->
                                sender.sendMessage(messages.messageFor(MessageKeys.REGISTER_FREEHOLD_ALREADY_REGISTERED));
                        case RealtyPaperApi.CreateFreeholdResult.Error error ->
                                sender.sendMessage(messages.messageFor(MessageKeys.REGISTER_FREEHOLD_ERROR,
                                        Placeholder.unparsed("error", error.message())));
                    }
                }).exceptionally(ex -> {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    cause.printStackTrace();
                    sender.sendMessage(messages.messageFor(MessageKeys.REGISTER_FREEHOLD_ERROR,
                            Placeholder.unparsed("error", cause.getMessage())));
                    return null;
                });
    }

}
