package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.api.ActorContext;
import io.github.md5sha256.realty.api.ExecutorState;
import io.github.md5sha256.realty.auth.ActorContexts;
import io.github.md5sha256.realty.api.CurrencyFormatter;
import io.github.md5sha256.realty.api.DurationFormatter;
import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.api.RealtyBackend;
import io.github.md5sha256.realty.api.RealtyPaperApi;
import io.github.md5sha256.realty.command.util.AuthorityParser;
import io.github.md5sha256.realty.command.util.DurationParser;
import io.github.md5sha256.realty.command.util.ParseBounds;
import io.github.md5sha256.realty.command.util.PartyFlag;
import io.github.md5sha256.realty.command.util.PartyFlags;
import io.github.md5sha256.realty.command.util.PartyResolver;
import io.github.md5sha256.realty.command.util.RegionOrFlagParser;
import io.github.md5sha256.realty.command.util.SetRouter;
import io.github.md5sha256.realty.command.util.SetRouting;
import io.github.md5sha256.realty.api.WorldGuardRegion;
import io.github.md5sha256.realty.api.event.LandlordSetEvent;
import io.github.md5sha256.realty.api.event.LeaseModificationProposedEvent;
import io.github.md5sha256.realty.api.event.LeaseModifyProposeEvent;
import io.github.md5sha256.realty.api.event.PriceChangedEvent;
import io.github.md5sha256.realty.api.event.PriceSetEvent;
import io.github.md5sha256.realty.api.event.TenantSetEvent;
import io.github.md5sha256.realty.api.event.TitleTransferEvent;
import io.github.md5sha256.realty.api.event.TitleTransferredEvent;
import io.github.md5sha256.realty.command.util.WorldGuardRegionResolver;
import io.github.md5sha256.realty.database.entity.LeaseholdContractEntity;
import io.github.md5sha256.realty.event.RealtyEventDispatch;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.util.PartyNames;
import io.github.md5sha256.realty.localisation.MessageKeys;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
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
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Groups all set-related subcommands under {@code /realty set}.
 *
 * <ul>
 *   <li>{@code /realty set price <price> [region] [--now]} — set freehold or leasehold price</li>
 *   <li>{@code /realty set duration <duration> [region] [--now]} — set leasehold duration</li>
 *   <li>{@code /realty set landlord <name> [region] [type flag]} — set leasehold landlord</li>
 *   <li>{@code /realty set titleholder <player> <region>} — set freehold title holder</li>
 *   <li>{@code /realty set tenant <player> <region>} — set leasehold tenant</li>
 *   <li>{@code /realty set maxextensions <count> [region] [--now]} — set leasehold max extensions (-1 for unlimited, which needs {@code --now})</li>
 *   <li>{@code /realty set authority <name> [region] [type flag]} — set freehold authority</li>
 * </ul>
 *
 * <p>A landlord or authority name is a player unless one of the type flags {@code --government},
 * {@code --business}, {@code --system} or {@code --group} is given; see {@link PartyFlags}. The type
 * flag comes last. When the region is left out, {@link RegionOrFlagParser} lets the flag through and
 * the region the player stands in is used.</p>
 *
 * <p>On a rented region a price, duration or max-extensions change waits for the next renewal unless
 * {@code --now} is given: a landlord's change is scheduled and a tenant's is requested, both through the
 * modification flow. {@link SetRouter} decides which.</p>
 */
public record SetCommandGroup(
        @NotNull RealtyPaperApi api,
        @NotNull ActorContexts actors,
        @NotNull PartyResolver partyResolver,
        @NotNull SuggestionProvider<Source> partySuggestions,
        @NotNull ExecutorState executorState,
        @NotNull MessageContainer messages,
        @NotNull RealtyEventDispatch events,
        @NotNull PartyNames partyNames,
        @NotNull SetRouter router
) implements CustomCommandBean {

    /**
     * {@code --now} applies a term change to a rented region at once instead of at the next renewal;
     * gated by {@code realty.command.set.now}.
     */
    static final CommandFlag<Void> NOW_FLAG = CommandFlag.<Source>builder("now").build();

    /**
     * The test a player's context must pass against a vacant leasehold's landlord before an instant
     * {@code /realty set}: acting for the landlord for the lease's terms, or being allowed to hand the
     * landlord's role on for {@code /realty set landlord}, since reassignment follows the money.
     */
    enum LandlordGate {
        MANAGES(ActorContext::mayManage),
        REASSIGNS(ActorContext::mayReassign);

        private final @NotNull BiPredicate<ActorContext, Party> test;

        LandlordGate(@NotNull BiPredicate<ActorContext, Party> test) {
            this.test = test;
        }

        boolean admits(@NotNull ActorContext actor, @NotNull Party landlord) {
            return this.test.test(actor, landlord);
        }
    }

    /** The gate {@code /realty set landlord} uses; every other subcommand uses {@link LandlordGate#MANAGES}. */
    static final LandlordGate SET_LANDLORD_GATE = LandlordGate.REASSIGNS;

    /**
     * Authorizes a leasehold {@code set} mutation, then runs {@code onAuthorized} with the actor's context.
     * Non-players (console) and admins holding {@code bypassPerm} are trusted. For a leasehold the
     * authority is the landlord, not WorldGuard ownership (the WorldGuard owner of an active lease is the
     * tenant): a vacant lease may be set instantly by anyone whose context passes {@code landlordGate}
     * against its landlord party (managing it for the terms and the tenant, reassigning it for
     * {@code /realty set landlord}), an occupied lease must use {@code /realty modify} so rents cannot be
     * changed mid-tenancy without notice. For a freehold/unregistered region this falls back to the
     * WorldGuard-owner check so title-holder-owned regions keep working. The context is built on the
     * database executor and also tests {@code extra}.
     *
     * <p>A non-null {@code leaseholdPerm} additionally gates an instant leasehold term change behind that
     * node, so it is refused (pointing at {@code /realty modify}) unless the caller holds it. It is set for
     * the terms {@code /realty modify} also covers (price, duration, max-extensions) and left {@code null}
     * for structural transfers (landlord, tenant), which have no {@code /modify} equivalent.</p>
     */
    private void authorizeLeaseholdSet(@NotNull CommandSender sender, @NotNull WorldGuardRegion region,
                                       @NotNull String bypassPerm, @Nullable String leaseholdPerm,
                                       @NotNull LandlordGate landlordGate,
                                       @NotNull Consumer<ActorContext> onAuthorized, @NotNull Party... extra) {
        if (!(sender instanceof Player player)) {
            onAuthorized.accept(ActorContext.console());
            return;
        }
        boolean bypass = player.hasPermission(bypassPerm);
        String regionId = region.region().getId();
        boolean isWorldGuardOwner = region.region().getOwners().contains(player.getUniqueId());
        CompletableFuture.supplyAsync(() -> actors.forRegion(player, bypass, region, extra), executorState.dbExec())
                .thenAcceptAsync(actor -> {
                    if (actor.bypass()) {
                        onAuthorized.accept(actor);
                    } else {
                        authorizeAsLandlord(player, region, regionId, isWorldGuardOwner, leaseholdPerm,
                                landlordGate, actor, onAuthorized);
                    }
                }, executorState.mainThreadExec())
                .exceptionally(ex -> {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    player.sendMessage(messages.messageFor(MessageKeys.COMMON_ERROR,
                            Placeholder.unparsed("error", String.valueOf(cause.getMessage()))));
                    return null;
                });
    }

    private void authorizeAsLandlord(@NotNull Player player, @NotNull WorldGuardRegion region,
                                     @NotNull String regionId, boolean isWorldGuardOwner,
                                     @Nullable String leaseholdPerm, @NotNull LandlordGate landlordGate,
                                     @NotNull ActorContext actor,
                                     @NotNull Consumer<ActorContext> onAuthorized) {
        api.getLeaseholdContract(regionId, region.world().getUID()).thenAccept(lease -> {
            if (lease != null) {
                // Some instant term changes on a leasehold require an extra node; without it the
                // change must go through /realty modify so it applies on the next cycle.
                if (leaseholdPerm != null && !player.hasPermission(leaseholdPerm)) {
                    player.sendMessage(messages.messageFor(MessageKeys.SET_LEASEHOLD_NO_PERMISSION,
                            Placeholder.unparsed("region", regionId)));
                } else if (lease.tenantId() != null) {
                    player.sendMessage(messages.messageFor(MessageKeys.SET_OCCUPIED_USE_MODIFY,
                            Placeholder.unparsed("region", regionId)));
                } else if (!landlordGate.admits(actor, lease.landlord())) {
                    player.sendMessage(messages.messageFor(MessageKeys.SET_NOT_LANDLORD,
                            Placeholder.unparsed("region", regionId)));
                } else {
                    onAuthorized.accept(actor);
                }
                return;
            }
            if (isWorldGuardOwner) {
                onAuthorized.accept(actor);
            } else {
                player.sendMessage(messages.messageFor(MessageKeys.SET_NO_PERMISSION));
            }
        });
    }

    @Override
    public @NotNull List<Command<? extends Source>> commands(@NotNull Command.Builder<Source> builder) {
        var base = builder
                .literal("set");
        var titleholderCommand = base.literal("titleholder")
                .permission("realty.command.set.titleholder")
                .required("titleholder", AuthorityParser.authority())
                .optional("region", WorldGuardRegionResolver.worldGuardRegionResolver())
                .handler(this::executeSetTitleHolder)
                .build();
        return List.of(
                base.literal("price")
                        .permission("realty.command.set.price")
                        .required("price", DoubleParser.doubleParser(ParseBounds.MIN_STRICTLY_POSITIVE,
                                Double.MAX_VALUE))
                        .optional("region", RegionOrFlagParser.regionOrFlag())
                        .flag(NOW_FLAG)
                        .handler(this::executeSetPrice)
                        .build(),
                base.literal("duration")
                        .permission("realty.command.set.duration")
                        .required("duration", DurationParser.duration())
                        .optional("region", RegionOrFlagParser.regionOrFlag())
                        .flag(NOW_FLAG)
                        .handler(this::executeSetDuration)
                        .build(),
                PartyFlags.addTo(base.literal("landlord")
                        .permission("realty.command.set.landlord")
                        .required("landlord", StringParser.stringParser(), partySuggestions)
                        .optional("region", RegionOrFlagParser.regionOrFlag()))
                        .handler(this::executeSetLandlord)
                        .build(),
                titleholderCommand,
                base.literal("tenant")
                        .permission("realty.command.set.tenant")
                        .required("tenant", AuthorityParser.authority())
                        .optional("region", WorldGuardRegionResolver.worldGuardRegionResolver())
                        .handler(this::executeSetTenant)
                        .build(),
                base.literal("maxextensions")
                        .permission("realty.command.set.maxextensions")
                        .required("maxextensions", IntegerParser.integerParser(-1))
                        .optional("region", RegionOrFlagParser.regionOrFlag())
                        .flag(NOW_FLAG)
                        .handler(this::executeSetMaxExtensions)
                        .build(),
                PartyFlags.addTo(base.literal("authority")
                        .permission("realty.command.set.authority")
                        .required("authority", StringParser.stringParser(), partySuggestions)
                        .optional("region", RegionOrFlagParser.regionOrFlag()))
                        .handler(this::executeSetAuthority)
                        .build()
        );
    }

    /** The one lease term a {@code set} command changes; exactly one component is non-null. */
    private record Term(@Nullable Double price, @Nullable Long durationSeconds,
                        @Nullable Integer maxExtensions) {}

    private void executeSetPrice(@NotNull CommandContext<Source> ctx) {
        double price = ctx.get("price");
        routeTerm(ctx, "realty.command.set.price.others", new Term(price, null, null), false,
                (region, routed) -> {
            CommandSender sender = ctx.sender().source();
            String regionId = region.region().getId();
            router.reportWriteFailure(api.setPrice(regionId, region.world().getUID(), price,
                    routed.actor(), routed.vacantOnly()).thenAccept(result -> {
                switch (result) {
                    case RealtyBackend.SetPriceResult.Success ignored -> {
                        sender.sendMessage(messages.messageFor(MessageKeys.SET_PRICE_SUCCESS,
                                Placeholder.unparsed("price", CurrencyFormatter.format(price)),
                                Placeholder.unparsed("region", regionId)));
                        events.fireSync(new PriceChangedEvent(region, price));
                    }
                    case RealtyBackend.SetPriceResult.NoContract ignored ->
                            sender.sendMessage(messages.messageFor(MessageKeys.SET_PRICE_NO_CONTRACT,
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyBackend.SetPriceResult.AuctionExists ignored ->
                            sender.sendMessage(messages.messageFor(MessageKeys.SET_PRICE_AUCTION_EXISTS,
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyBackend.SetPriceResult.OfferPaymentInProgress ignored ->
                            sender.sendMessage(messages.messageFor(
                                    MessageKeys.SET_PRICE_OFFER_PAYMENT_IN_PROGRESS,
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyBackend.SetPriceResult.BidPaymentInProgress ignored ->
                            sender.sendMessage(messages.messageFor(
                                    MessageKeys.SET_PRICE_BID_PAYMENT_IN_PROGRESS,
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyBackend.SetPriceResult.NotAuthorized ignored ->
                            sendNotHolder(sender, routed, regionId);
                    case RealtyBackend.SetPriceResult.Occupied ignored ->
                            sender.sendMessage(messages.messageFor(MessageKeys.SET_JUST_RENTED,
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyBackend.SetPriceResult.UpdateFailed ignored ->
                            sender.sendMessage(messages.messageFor(MessageKeys.SET_PRICE_UPDATE_FAILED,
                                    Placeholder.unparsed("region", regionId)));
                }
            }), sender, MessageKeys.SET_PRICE_ERROR);
        });
    }

    private void executeSetDuration(@NotNull CommandContext<Source> ctx) {
        Duration duration = ctx.get("duration");
        routeTerm(ctx, "realty.command.set.duration.others", new Term(null, duration.toSeconds(), null), false,
                (region, routed) -> {
            CommandSender sender = ctx.sender().source();
            String regionId = region.region().getId();
            router.reportWriteFailure(api.setDuration(regionId, region.world().getUID(), duration.toSeconds(),
                    routed.actor(), routed.vacantOnly()).thenAccept(result -> {
                switch (result) {
                    case RealtyBackend.SetDurationResult.Success ignored ->
                            sender.sendMessage(messages.messageFor(MessageKeys.SET_DURATION_SUCCESS,
                                    Placeholder.unparsed("duration", DurationFormatter.format(duration)),
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyBackend.SetDurationResult.NoLeaseholdContract ignored ->
                            sender.sendMessage(messages.messageFor(
                                    MessageKeys.SET_DURATION_NO_LEASEHOLD_CONTRACT,
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyBackend.SetDurationResult.NotAuthorized ignored ->
                            sendNotHolder(sender, routed, regionId);
                    case RealtyBackend.SetDurationResult.Occupied ignored ->
                            sender.sendMessage(messages.messageFor(MessageKeys.SET_JUST_RENTED,
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyBackend.SetDurationResult.UpdateFailed ignored ->
                            sender.sendMessage(messages.messageFor(MessageKeys.SET_DURATION_UPDATE_FAILED,
                                    Placeholder.unparsed("region", regionId)));
                }
            }), sender, MessageKeys.SET_DURATION_ERROR);
        });
    }

    /**
     * Shared by {@code set price}, {@code set duration} and {@code set maxextensions}: finds the region,
     * asks the router what the sender may do, and either calls {@code applyNow} or proposes the change
     * for the next renewal (a landlord schedules it, a tenant asks the landlord for it).
     */
    private void routeTerm(@NotNull CommandContext<Source> ctx, @NotNull String othersPermission,
                           @NotNull Term term, boolean unlimitedExtensions,
                           @NotNull BiConsumer<WorldGuardRegion, SetRouter.Routed> applyNow) {
        CommandSender sender = ctx.sender().source();
        WorldGuardRegion region = ctx.<Optional<WorldGuardRegion>>optional("region")
                .flatMap(Function.identity())
                .orElseGet(() -> sender instanceof Player player
                        ? WorldGuardRegionResolver.resolveAtLocation(player.getLocation()) : null);
        if (region == null) {
            sender.sendMessage(messages.messageFor(MessageKeys.ERROR_NO_REGION));
            return;
        }
        router.route(sender, region, SetRouting.Kind.TERM, SetRouter.HolderTest.MANAGES, othersPermission,
                MessageKeys.SET_NO_PERMISSION, ctx.flags().hasFlag(NOW_FLAG), unlimitedExtensions, routed -> {
            // Both run on the main thread, where a listener may cancel.
            if (term.price() != null && sender instanceof Player player
                    && !events.fireSync(new PriceSetEvent(region, player.getUniqueId(), term.price()))) {
                sender.sendMessage(messages.messageFor(MessageKeys.COMMON_ACTION_CANCELLED));
                return;
            }
            if (routed.outcome() instanceof SetRouting.Outcome.ApplyNow) {
                applyNow.accept(region, routed);
            } else {
                propose(sender, region, routed, term);
            }
        });
    }

    private void sendNotHolder(@NotNull CommandSender sender, @NotNull SetRouter.Routed routed,
                               @NotNull String regionId) {
        String key = routed.lease() != null ? MessageKeys.SET_NOT_LANDLORD : MessageKeys.SET_NO_PERMISSION;
        sender.sendMessage(messages.messageFor(key, Placeholder.unparsed("region", regionId)));
    }

    /** Proposes {@code term} through the modification flow, as {@code /realty modify} does. */
    private void propose(@NotNull CommandSender sender, @NotNull WorldGuardRegion region,
                         @NotNull SetRouter.Routed routed, @NotNull Term term) {
        LeaseholdContractEntity lease = Objects.requireNonNull(routed.lease(),
                "a proposal is only routed for a lease");
        // Only the console has no id of its own; the router lets it schedule only for a player landlord.
        UUID actingPlayer = sender instanceof Player player
                ? player.getUniqueId()
                : ((Party.Personal) lease.landlord()).playerUuid();
        if (!events.fireSync(new LeaseModifyProposeEvent(region, actingPlayer))) {
            sender.sendMessage(messages.messageFor(MessageKeys.COMMON_ACTION_CANCELLED));
            return;
        }
        String regionId = region.region().getId();
        router.reportWriteFailure(api.proposeModification(regionId, region.world().getUID(), routed.actor(),
                term.price(), term.durationSeconds(), term.maxExtensions()).thenAccept(result -> {
            switch (result) {
                case RealtyBackend.ProposeModificationResult.Success success -> {
                    String key = success.active()
                            ? MessageKeys.MODIFY_PROPOSE_SUCCESS_LANDLORD
                            : MessageKeys.MODIFY_PROPOSE_SUCCESS_TENANT;
                    sender.sendMessage(messages.messageFor(key, Placeholder.unparsed("region", regionId)));
                    events.fireSync(new LeaseModificationProposedEvent(region, success.proposerRole(),
                            actingPlayer, success.landlord(), success.tenantId(), success.active()));
                }
                case RealtyBackend.ProposeModificationResult.NoLeaseholdContract ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.MODIFY_NO_LEASEHOLD_CONTRACT,
                                Placeholder.unparsed("region", regionId)));
                case RealtyBackend.ProposeModificationResult.NotOccupied ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.SET_JUST_VACATED,
                                Placeholder.unparsed("region", regionId)));
                case RealtyBackend.ProposeModificationResult.Terminating ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.MODIFY_TERMINATING,
                                Placeholder.unparsed("region", regionId)));
                case RealtyBackend.ProposeModificationResult.NotAuthorized ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.MODIFY_NOT_AUTHORIZED,
                                Placeholder.unparsed("region", regionId)));
                case RealtyBackend.ProposeModificationResult.UpdateFailed ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.MODIFY_UPDATE_FAILED,
                                Placeholder.unparsed("region", regionId)));
            }
        }), sender, MessageKeys.MODIFY_ERROR);
    }

    private void executeSetLandlord(@NotNull CommandContext<Source> ctx) {
        CommandSender sender = ctx.sender().source();
        if (!(PartyFlags.read(ctx) instanceof PartyFlags.Read.One(PartyFlag flag))) {
            sender.sendMessage(messages.messageFor(MessageKeys.PARTY_MULTIPLE_TYPE_FLAGS));
            return;
        }
        String landlordName = ctx.get("landlord");
        WorldGuardRegion region = ctx.<Optional<WorldGuardRegion>>optional("region")
                .flatMap(Function.identity())
                .orElseGet(() -> sender instanceof Player player
                        ? WorldGuardRegionResolver.resolveAtLocation(player.getLocation()) : null);
        if (region == null) {
            sender.sendMessage(messages.messageFor(MessageKeys.ERROR_NO_REGION));
            return;
        }
        PartyFlags.resolveThen(partyResolver, executorState, messages, sender, landlordName, flag, newLandlord ->
        // The current landlord must be one the player may reassign; the new landlord is tested too: a
        // non-admin may only hand the lease to a party they manage.
        authorizeLeaseholdSet(sender, region, "realty.command.set.landlord.others", null, SET_LANDLORD_GATE,
                actor ->
        api.setLandlord(region, newLandlord, actor).thenAccept(result -> {
            switch (result) {
                case RealtyPaperApi.SetLandlordResult.Success success -> {
                        sender.sendMessage(messages.messageFor(MessageKeys.SET_LANDLORD_SUCCESS,
                                Placeholder.unparsed("landlord", partyNames.display(newLandlord)),
                                Placeholder.unparsed("region", success.regionId())));
                        events.fireSync(new LandlordSetEvent(region, newLandlord, success.previousLandlord()));
                }
                case RealtyPaperApi.SetLandlordResult.NoLeaseholdContract noContract ->
                        sender.sendMessage(messages.messageFor(MessageKeys.SET_LANDLORD_NO_LEASEHOLD_CONTRACT,
                                Placeholder.unparsed("region", noContract.regionId())));
                case RealtyPaperApi.SetLandlordResult.Occupied occupied ->
                        sender.sendMessage(messages.messageFor(MessageKeys.SET_JUST_RENTED,
                                Placeholder.unparsed("region", occupied.regionId())));
                case RealtyPaperApi.SetLandlordResult.UpdateFailed updateFailed ->
                        sender.sendMessage(messages.messageFor(MessageKeys.SET_LANDLORD_UPDATE_FAILED,
                                Placeholder.unparsed("region", updateFailed.regionId())));
                case RealtyPaperApi.SetLandlordResult.NotAllowedToReassign notAllowed ->
                        sender.sendMessage(messages.messageFor(MessageKeys.PARTY_NOT_ALLOWED_TO_REASSIGN,
                                Placeholder.unparsed("name", partyNames.display(notAllowed.current()))));
                case RealtyPaperApi.SetLandlordResult.NotAllowedToAssign ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.PARTY_NOT_ALLOWED_TO_ASSIGN,
                                Placeholder.unparsed("name", landlordName)));
                case RealtyPaperApi.SetLandlordResult.Error error ->
                        sender.sendMessage(messages.messageFor(MessageKeys.SET_LANDLORD_ERROR,
                                Placeholder.unparsed("error", error.message())));
            }
        }), newLandlord));
    }

    private void executeSetTitleHolder(@NotNull CommandContext<Source> ctx) {
        CommandSender sender = ctx.sender().source();
        UUID titleHolderId = ctx.get("titleholder");
        WorldGuardRegion region = ctx.<WorldGuardRegion>optional("region")
                .orElseGet(() -> sender instanceof Player player
                        ? WorldGuardRegionResolver.resolveAtLocation(player.getLocation()) : null);
        if (region == null) {
            sender.sendMessage(messages.messageFor(MessageKeys.ERROR_NO_REGION));
            return;
        }
        if (sender instanceof Player player
                && !sender.hasPermission("realty.command.set.titleholder.others")
                && !region.region().getOwners().contains(player.getUniqueId())) {
            sender.sendMessage(messages.messageFor(MessageKeys.SET_NO_PERMISSION));
            return;
        }
        if (!events.fireSync(new TitleTransferEvent(region, titleHolderId))) {
            sender.sendMessage(messages.messageFor(MessageKeys.COMMON_ACTION_CANCELLED));
            return;
        }
        api.setTitleHolder(region, Party.personal(titleHolderId)).thenAccept(result -> {
            switch (result) {
                case RealtyPaperApi.SetTitleHolderResult.Success success -> {
                        sender.sendMessage(messages.messageFor(MessageKeys.SET_TITLEHOLDER_SUCCESS,
                                Placeholder.unparsed("titleholder", partyNames.display(titleHolderId)),
                                Placeholder.unparsed("region", success.regionId())));
                        events.fireSync(new TitleTransferredEvent(region, titleHolderId,
                                Party.playerUuidOf(success.previousTitleHolder()).orElse(null)));
                }
                case RealtyPaperApi.SetTitleHolderResult.NoFreeholdContract noContract ->
                        sender.sendMessage(messages.messageFor(MessageKeys.SET_TITLEHOLDER_NO_FREEHOLD_CONTRACT,
                                Placeholder.unparsed("region", noContract.regionId())));
                case RealtyPaperApi.SetTitleHolderResult.NotAuthorized ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.SET_NO_PERMISSION));
                case RealtyPaperApi.SetTitleHolderResult.UpdateFailed updateFailed ->
                        sender.sendMessage(messages.messageFor(MessageKeys.SET_TITLEHOLDER_UPDATE_FAILED,
                                Placeholder.unparsed("region", updateFailed.regionId())));
                case RealtyPaperApi.SetTitleHolderResult.Error error ->
                        sender.sendMessage(messages.messageFor(MessageKeys.SET_TITLEHOLDER_ERROR,
                                Placeholder.unparsed("error", error.message())));
            }
        });
    }

    private void executeSetTenant(@NotNull CommandContext<Source> ctx) {
        CommandSender sender = ctx.sender().source();
        UUID tenantId = ctx.get("tenant");
        WorldGuardRegion region = ctx.<WorldGuardRegion>optional("region")
                .orElseGet(() -> sender instanceof Player player
                        ? WorldGuardRegionResolver.resolveAtLocation(player.getLocation()) : null);
        if (region == null) {
            sender.sendMessage(messages.messageFor(MessageKeys.ERROR_NO_REGION));
            return;
        }
        authorizeLeaseholdSet(sender, region, "realty.command.set.tenant.others", null, LandlordGate.MANAGES,
                _ ->
        api.setTenant(region, Party.personal(tenantId)).thenAccept(result -> {
            switch (result) {
                case RealtyPaperApi.SetTenantResult.Success success -> {
                        sender.sendMessage(messages.messageFor(MessageKeys.SET_TENANT_SUCCESS,
                                Placeholder.unparsed("tenant", partyNames.display(tenantId)),
                                Placeholder.unparsed("region", success.regionId())));
                        events.fireSync(new TenantSetEvent(region, tenantId, Party.playerUuidOf(success.previousTenant()).orElse(null),
                                success.landlord()));
                }
                case RealtyPaperApi.SetTenantResult.NoLeaseholdContract noContract ->
                        sender.sendMessage(messages.messageFor(MessageKeys.SET_TENANT_NO_LEASEHOLD_CONTRACT,
                                Placeholder.unparsed("region", noContract.regionId())));
                case RealtyPaperApi.SetTenantResult.NotAuthorized ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.SET_NO_PERMISSION));
                case RealtyPaperApi.SetTenantResult.Occupied occupied ->
                        sender.sendMessage(messages.messageFor(MessageKeys.SET_JUST_RENTED,
                                Placeholder.unparsed("region", occupied.regionId())));
                case RealtyPaperApi.SetTenantResult.UpdateFailed updateFailed ->
                        sender.sendMessage(messages.messageFor(MessageKeys.SET_TENANT_UPDATE_FAILED,
                                Placeholder.unparsed("region", updateFailed.regionId())));
                case RealtyPaperApi.SetTenantResult.Error error ->
                        sender.sendMessage(messages.messageFor(MessageKeys.SET_TENANT_ERROR,
                                Placeholder.unparsed("error", error.message())));
            }
        }));
    }

    private void executeSetMaxExtensions(@NotNull CommandContext<Source> ctx) {
        int maxExtensions = ctx.get("maxextensions");
        routeTerm(ctx, "realty.command.set.maxextensions.others", new Term(null, null, maxExtensions),
                maxExtensions < 0, (region, routed) -> {
            CommandSender sender = ctx.sender().source();
            String regionId = region.region().getId();
            router.reportWriteFailure(api.setMaxRenewals(regionId, region.world().getUID(), maxExtensions,
                    routed.actor(), routed.vacantOnly()).thenAccept(result -> {
                switch (result) {
                    case RealtyBackend.SetMaxRenewalsResult.Success ignored ->
                            sender.sendMessage(messages.messageFor(MessageKeys.SET_MAX_EXTENSIONS_SUCCESS,
                                    Placeholder.unparsed("maxextensions",
                                            maxExtensions < 0 ? "unlimited" : String.valueOf(maxExtensions)),
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyBackend.SetMaxRenewalsResult.NoLeaseholdContract ignored ->
                            sender.sendMessage(messages.messageFor(
                                    MessageKeys.SET_MAX_EXTENSIONS_NO_LEASEHOLD_CONTRACT,
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyBackend.SetMaxRenewalsResult.BelowCurrentExtensions(int current) ->
                            sender.sendMessage(messages.messageFor(MessageKeys.SET_MAX_EXTENSIONS_BELOW_CURRENT,
                                    Placeholder.unparsed("current", String.valueOf(current)),
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyBackend.SetMaxRenewalsResult.NotAuthorized ignored ->
                            sendNotHolder(sender, routed, regionId);
                    case RealtyBackend.SetMaxRenewalsResult.Occupied ignored ->
                            sender.sendMessage(messages.messageFor(MessageKeys.SET_JUST_RENTED,
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyBackend.SetMaxRenewalsResult.UpdateFailed ignored ->
                            sender.sendMessage(messages.messageFor(
                                    MessageKeys.SET_MAX_EXTENSIONS_UPDATE_FAILED,
                                    Placeholder.unparsed("region", regionId)));
                }
            }), sender, MessageKeys.SET_MAX_EXTENSIONS_ERROR);
        });
    }

    /**
     * The permission {@code realty.command.set.authority} alone decides who may run this: whoever
     * holds it may set the authority of any region to any party, so no ownership or assignment
     * check is made here.
     */
    private void executeSetAuthority(@NotNull CommandContext<Source> ctx) {
        CommandSender sender = ctx.sender().source();
        if (!(PartyFlags.read(ctx) instanceof PartyFlags.Read.One(PartyFlag flag))) {
            sender.sendMessage(messages.messageFor(MessageKeys.PARTY_MULTIPLE_TYPE_FLAGS));
            return;
        }
        String authorityName = ctx.get("authority");
        WorldGuardRegion region = ctx.<Optional<WorldGuardRegion>>optional("region")
                .flatMap(Function.identity())
                .orElseGet(() -> sender instanceof Player player
                        ? WorldGuardRegionResolver.resolveAtLocation(player.getLocation()) : null);
        if (region == null) {
            sender.sendMessage(messages.messageFor(MessageKeys.ERROR_NO_REGION));
            return;
        }
        String regionId = region.region().getId();
        UUID worldId = region.world().getUID();
        PartyFlags.resolveThen(partyResolver, executorState, messages, sender, authorityName, flag, authority ->
        api.setAuthority(regionId, worldId, authority).thenAccept(result -> {
            switch (result) {
                case RealtyBackend.SetAuthorityResult.Success ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.SET_AUTHORITY_SUCCESS,
                                Placeholder.unparsed("authority", partyNames.display(authority)),
                                Placeholder.unparsed("region", regionId)));
                case RealtyBackend.SetAuthorityResult.NoFreeholdContract ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.SET_AUTHORITY_NO_FREEHOLD_CONTRACT,
                                Placeholder.unparsed("region", regionId)));
                case RealtyBackend.SetAuthorityResult.UpdateFailed ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.SET_AUTHORITY_UPDATE_FAILED,
                                Placeholder.unparsed("region", regionId)));
            }
        }));
    }

}
