package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.api.ExecutorState;
import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.api.RealtyPaperApi;
import io.github.md5sha256.realty.api.event.RegionCreateEvent;
import io.github.md5sha256.realty.api.event.RegionCreatedEvent;
import io.github.md5sha256.realty.command.util.AuthorityParser;
import io.github.md5sha256.realty.command.util.DurationParser;
import io.github.md5sha256.realty.command.util.ParseBounds;
import io.github.md5sha256.realty.command.util.PartyFlag;
import io.github.md5sha256.realty.command.util.PartyFlags;
import io.github.md5sha256.realty.command.util.PartyResolver;
import io.github.md5sha256.realty.api.WorldGuardRegion;
import io.github.md5sha256.realty.command.util.WorldGuardRegionResolver;
import io.github.md5sha256.realty.event.RealtyEventDispatch;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.localisation.MessageKeys;
import io.github.md5sha256.realty.settings.DefaultParties;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.component.CommandComponent;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.key.CloudKey;
import org.incendo.cloud.paper.util.sender.Source;
import org.incendo.cloud.parser.flag.CommandFlag;
import org.incendo.cloud.parser.standard.DoubleParser;
import org.incendo.cloud.parser.standard.IntegerParser;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Handles {@code /realty register leasehold <price> <period> <maxextensions> <region>}
 * and {@code /realty register freehold [--price <price>] [--titleholder <name>] [--authority <name>] <region>}.
 *
 * <p>One type flag ({@code --government}, {@code --business}, {@code --system} or {@code --group}) makes
 * the {@code --landlord} of a leasehold or the {@code --authority} of a freehold an account or a group
 * instead of a player. Without the flag it describes, the default from settings.yml is used.</p>
 *
 * <p>Permissions: {@code realty.command.register.leasehold} / {@code realty.command.register.freehold}.</p>
 */
public record RegisterCommand(@NotNull RealtyPaperApi api,
                              @NotNull AtomicReference<DefaultParties> defaults,
                              @NotNull PartyResolver partyResolver,
                              @NotNull SuggestionProvider<Source> partySuggestions,
                              @NotNull ExecutorState executorState,
                              @NotNull MessageContainer messages,
                              @NotNull RealtyEventDispatch events) implements CustomCommandBean {

    private static final CloudKey<Double> PRICE = CloudKey.of("price", Double.class);
    private static final CloudKey<Duration> PERIOD = CloudKey.of("period", Duration.class);
    private static final CloudKey<Integer> MAX_EXTENSIONS = CloudKey.of("maxrenewals", Integer.class);
    private static final CommandFlag<UUID> TITLEHOLDER_FLAG =
            CommandFlag.<Source>builder("titleholder")
                    .withComponent(AuthorityParser.authority())
                    .build();

    private static final CommandFlag<Double> PRICE_FLAG =
            CommandFlag.<Source>builder("price")
                    .withComponent(DoubleParser.doubleParser(ParseBounds.MIN_STRICTLY_POSITIVE,
                            Double.MAX_VALUE))
                    .build();

    private static final String LANDLORD_FLAG = "landlord";
    private static final String AUTHORITY_FLAG = "authority";

    /** A {@code --<role> <name>} flag whose name is resolved to a party in the handler. */
    private @NotNull CommandFlag<String> partyNameFlag(@NotNull String role) {
        return CommandFlag.<Source>builder(role)
                .withComponent(CommandComponent.<Source, String>builder(role, StringParser.stringParser())
                        .suggestionProvider(partySuggestions))
                .build();
    }

    @Override
    public @NotNull List<Command<? extends Source>> commands(@NotNull Command.Builder<Source> builder) {
        var base = builder
                .literal("register");
        return List.of(
                PartyFlags.addTo(base.literal("leasehold")
                        .permission("realty.command.register.leasehold")
                        .required(PRICE, DoubleParser.doubleParser(ParseBounds.MIN_STRICTLY_POSITIVE,
                                Double.MAX_VALUE))
                        .required(PERIOD, DurationParser.duration())
                        .required(MAX_EXTENSIONS, IntegerParser.integerParser(-1))
                        .flag(partyNameFlag(LANDLORD_FLAG))
                        .optional("region", WorldGuardRegionResolver.worldGuardRegionResolver()))
                        .handler(this::executeLeasehold)
                        .build(),
                PartyFlags.addTo(base.literal("freehold")
                        .permission("realty.command.register.freehold")
                        .flag(PRICE_FLAG)
                        .flag(TITLEHOLDER_FLAG)
                        .flag(partyNameFlag(AUTHORITY_FLAG))
                        .optional("region", WorldGuardRegionResolver.worldGuardRegionResolver()))
                        .handler(this::executeFreehold)
                        .build()
        );
    }

    private void executeLeasehold(@NotNull CommandContext<Source> ctx) {
        CommandSender sender = ctx.sender().source();
        if (!(PartyFlags.read(ctx) instanceof PartyFlags.Read.One(PartyFlag flag))) {
            sender.sendMessage(messages.messageFor(MessageKeys.PARTY_MULTIPLE_TYPE_FLAGS));
            return;
        }
        double price = ctx.get(PRICE);
        Duration period = ctx.get(PERIOD);
        int maxExtensions = ctx.get(MAX_EXTENSIONS);
        WorldGuardRegion region = ctx.<WorldGuardRegion>optional("region")
                .orElseGet(() -> sender instanceof Player player
                        ? WorldGuardRegionResolver.resolveAtLocation(player.getLocation()) : null);
        if (region == null) {
            sender.sendMessage(messages.messageFor(MessageKeys.ERROR_NO_REGION));
            return;
        }
        String landlordName = ctx.flags().getValue(LANDLORD_FLAG, null);
        resolvePartyOrDefault(sender, LANDLORD_FLAG, landlordName, flag, defaults.get().leaseholdLandlord(),
                landlord -> registerLeasehold(sender, region, price, period, maxExtensions, landlord));
    }

    private void registerLeasehold(@NotNull CommandSender sender, @NotNull WorldGuardRegion region, double price,
                                   @NotNull Duration period, int maxExtensions, @NotNull Party landlord) {
        if (sender instanceof Player player
                && !events.fireSync(new RegionCreateEvent(region, player.getUniqueId()))) {
            sender.sendMessage(messages.messageFor(MessageKeys.COMMON_ACTION_CANCELLED));
            return;
        }
        api.registerLeasehold(region, price, period.toSeconds(), maxExtensions, landlord)
                .thenAccept(result -> {
                    switch (result) {
                        case RealtyPaperApi.CreateLeaseholdResult.Success ignored -> {
                                sender.sendMessage(messages.messageFor(MessageKeys.REGISTER_RENTAL_SUCCESS));
                                if (sender instanceof Player player) {
                                    events.fireSync(new RegionCreatedEvent(region, player.getUniqueId()));
                                }
                        }
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
        if (!(PartyFlags.read(ctx) instanceof PartyFlags.Read.One(PartyFlag flag))) {
            sender.sendMessage(messages.messageFor(MessageKeys.PARTY_MULTIPLE_TYPE_FLAGS));
            return;
        }
        Double price = ctx.flags().getValue(PRICE_FLAG, null);
        UUID titleholder = ctx.flags()
                .getValue(TITLEHOLDER_FLAG, defaults.get().freeholdTitleholder());
        WorldGuardRegion region = ctx.<WorldGuardRegion>optional("region")
                .orElseGet(() -> sender instanceof Player player
                        ? WorldGuardRegionResolver.resolveAtLocation(player.getLocation()) : null);
        if (region == null) {
            sender.sendMessage(messages.messageFor(MessageKeys.ERROR_NO_REGION));
            return;
        }
        String authorityName = ctx.flags().getValue(AUTHORITY_FLAG, null);
        resolvePartyOrDefault(sender, AUTHORITY_FLAG, authorityName, flag, defaults.get().freeholdAuthority(),
                authority -> registerFreehold(sender, region, price, authority, titleholder));
    }

    private void registerFreehold(@NotNull CommandSender sender, @NotNull WorldGuardRegion region,
                                  @Nullable Double price, @NotNull Party authority, @Nullable UUID titleholder) {
        if (sender instanceof Player player
                && !events.fireSync(new RegionCreateEvent(region, player.getUniqueId()))) {
            sender.sendMessage(messages.messageFor(MessageKeys.COMMON_ACTION_CANCELLED));
            return;
        }
        api.registerFreehold(region, price, authority, titleholder)
                .thenAccept(result -> {
                    switch (result) {
                        case RealtyPaperApi.CreateFreeholdResult.Success ignored -> {
                                sender.sendMessage(messages.messageFor(MessageKeys.REGISTER_FREEHOLD_SUCCESS));
                                if (sender instanceof Player player) {
                                    events.fireSync(new RegionCreatedEvent(region, player.getUniqueId()));
                                }
                        }
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

    /**
     * Passes {@code onResolved} the party named by {@code --<role>}, resolved with the type flag, or
     * the default from settings.yml when the flag was not given. A type flag without a name, a
     * default that could not be resolved, and a refused name each end the command with a message.
     */
    private void resolvePartyOrDefault(@NotNull CommandSender sender, @NotNull String role, @Nullable String name,
                                       @Nullable PartyFlag flag, @Nullable Party fallback,
                                       @NotNull Consumer<Party> onResolved) {
        if (name != null) {
            PartyFlags.resolveThen(partyResolver, executorState, messages, sender, name, flag, onResolved);
        } else if (flag != null) {
            sender.sendMessage(messages.messageFor(MessageKeys.PARTY_TYPE_FLAG_WITHOUT_NAME,
                    Placeholder.unparsed("role", role)));
        } else if (fallback == null) {
            sender.sendMessage(messages.messageFor(MessageKeys.ERROR_DEFAULT_PARTY_UNRESOLVED,
                    Placeholder.unparsed("role", role)));
        } else {
            onResolved.accept(fallback);
        }
    }

}
