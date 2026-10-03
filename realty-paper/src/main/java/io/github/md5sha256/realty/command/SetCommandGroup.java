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
import io.github.md5sha256.realty.api.WorldGuardRegion;
import io.github.md5sha256.realty.api.event.LandlordSetEvent;
import io.github.md5sha256.realty.api.event.PriceChangedEvent;
import io.github.md5sha256.realty.api.event.PriceSetEvent;
import io.github.md5sha256.realty.api.event.TenantSetEvent;
import io.github.md5sha256.realty.api.event.TitleTransferEvent;
import io.github.md5sha256.realty.api.event.TitleTransferredEvent;
import io.github.md5sha256.realty.command.util.WorldGuardRegionResolver;
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
import org.incendo.cloud.parser.standard.DoubleParser;
import org.incendo.cloud.parser.standard.IntegerParser;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Groups all set-related subcommands under {@code /realty set}.
 *
 * <ul>
 *   <li>{@code /realty set price <price> <region>} — set freehold or leasehold price</li>
 *   <li>{@code /realty set duration <duration> <region>} — set leasehold duration</li>
 *   <li>{@code /realty set landlord <name> [region] [type flag]} — set leasehold landlord</li>
 *   <li>{@code /realty set titleholder <player> <region>} — set freehold title holder</li>
 *   <li>{@code /realty set tenant <player> <region>} — set leasehold tenant</li>
 *   <li>{@code /realty set maxextensions <count> <region>} — set leasehold max extensions (-1 for unlimited)</li>
 *   <li>{@code /realty set authority <name> [region] [type flag]} — set freehold authority</li>
 * </ul>
 *
 * <p>A landlord or authority name is a player unless one of the type flags {@code --government},
 * {@code --business}, {@code --system} or {@code --group} is given; see {@link PartyFlags}. The type
 * flag comes last. When the region is left out, {@link RegionOrFlagParser} lets the flag through and
 * the region the player stands in is used.</p>
 */
public record SetCommandGroup(
        @NotNull RealtyPaperApi api,
        @NotNull ActorContexts actors,
        @NotNull PartyResolver partyResolver,
        @NotNull SuggestionProvider<Source> partySuggestions,
        @NotNull ExecutorState executorState,
        @NotNull MessageContainer messages,
        @NotNull RealtyEventDispatch events,
        @NotNull PartyNames partyNames
) implements CustomCommandBean {

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
                        .optional("region", WorldGuardRegionResolver.worldGuardRegionResolver())
                        .handler(this::executeSetPrice)
                        .build(),
                base.literal("duration")
                        .permission("realty.command.set.duration")
                        .required("duration", DurationParser.duration())
                        .optional("region", WorldGuardRegionResolver.worldGuardRegionResolver())
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
                        .optional("region", WorldGuardRegionResolver.worldGuardRegionResolver())
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

    private void executeSetPrice(@NotNull CommandContext<Source> ctx) {
        CommandSender sender = ctx.sender().source();
        double price = ctx.get("price");
        WorldGuardRegion region = ctx.<WorldGuardRegion>optional("region")
                .orElseGet(() -> sender instanceof Player player
                        ? WorldGuardRegionResolver.resolveAtLocation(player.getLocation()) : null);
        if (region == null) {
            sender.sendMessage(messages.messageFor(MessageKeys.ERROR_NO_REGION));
            return;
        }
        String regionId = region.region().getId();
        UUID worldId = region.world().getUID();
        if (sender instanceof Player player
                && !events.fireSync(new PriceSetEvent(region, player.getUniqueId(), price))) {
            sender.sendMessage(messages.messageFor(MessageKeys.COMMON_ACTION_CANCELLED));
            return;
        }
        authorizeLeaseholdSet(sender, region, "realty.command.set.price.others",
                "realty.command.set.price.leasehold", LandlordGate.MANAGES, _ ->
        api.setPrice(regionId, worldId, price).thenAccept(result -> {
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
                        sender.sendMessage(messages.messageFor(MessageKeys.SET_PRICE_OFFER_PAYMENT_IN_PROGRESS,
                                Placeholder.unparsed("region", regionId)));
                case RealtyBackend.SetPriceResult.BidPaymentInProgress ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.SET_PRICE_BID_PAYMENT_IN_PROGRESS,
                                Placeholder.unparsed("region", regionId)));
                case RealtyBackend.SetPriceResult.UpdateFailed ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.SET_PRICE_UPDATE_FAILED,
                                Placeholder.unparsed("region", regionId)));
            }
        }));
    }

    private void executeSetDuration(@NotNull CommandContext<Source> ctx) {
        CommandSender sender = ctx.sender().source();
        Duration duration = ctx.get("duration");
        WorldGuardRegion region = ctx.<WorldGuardRegion>optional("region")
                .orElseGet(() -> sender instanceof Player player
                        ? WorldGuardRegionResolver.resolveAtLocation(player.getLocation()) : null);
        if (region == null) {
            sender.sendMessage(messages.messageFor(MessageKeys.ERROR_NO_REGION));
            return;
        }
        String regionId = region.region().getId();
        UUID worldId = region.world().getUID();
        authorizeLeaseholdSet(sender, region, "realty.command.set.duration.others",
                "realty.command.set.duration.leasehold", LandlordGate.MANAGES, _ ->
        api.setDuration(regionId, worldId, duration.toSeconds()).thenAccept(result -> {
            switch (result) {
                case RealtyBackend.SetDurationResult.Success ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.SET_DURATION_SUCCESS,
                                Placeholder.unparsed("duration", DurationFormatter.format(duration)),
                                Placeholder.unparsed("region", regionId)));
                case RealtyBackend.SetDurationResult.NoLeaseholdContract ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.SET_DURATION_NO_LEASEHOLD_CONTRACT,
                                Placeholder.unparsed("region", regionId)));
                case RealtyBackend.SetDurationResult.UpdateFailed ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.SET_DURATION_UPDATE_FAILED,
                                Placeholder.unparsed("region", regionId)));
            }
        }));
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
        api.setTitleHolder(region, titleHolderId).thenAccept(result -> {
            switch (result) {
                case RealtyPaperApi.SetTitleHolderResult.Success success -> {
                        sender.sendMessage(messages.messageFor(MessageKeys.SET_TITLEHOLDER_SUCCESS,
                                Placeholder.unparsed("titleholder", partyNames.display(titleHolderId)),
                                Placeholder.unparsed("region", success.regionId())));
                        events.fireSync(new TitleTransferredEvent(region, titleHolderId,
                                success.previousTitleHolder()));
                }
                case RealtyPaperApi.SetTitleHolderResult.NoFreeholdContract noContract ->
                        sender.sendMessage(messages.messageFor(MessageKeys.SET_TITLEHOLDER_NO_FREEHOLD_CONTRACT,
                                Placeholder.unparsed("region", noContract.regionId())));
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
        api.setTenant(region, tenantId).thenAccept(result -> {
            switch (result) {
                case RealtyPaperApi.SetTenantResult.Success success -> {
                        sender.sendMessage(messages.messageFor(MessageKeys.SET_TENANT_SUCCESS,
                                Placeholder.unparsed("tenant", partyNames.display(tenantId)),
                                Placeholder.unparsed("region", success.regionId())));
                        events.fireSync(new TenantSetEvent(region, tenantId, success.previousTenant(),
                                success.landlord()));
                }
                case RealtyPaperApi.SetTenantResult.NoLeaseholdContract noContract ->
                        sender.sendMessage(messages.messageFor(MessageKeys.SET_TENANT_NO_LEASEHOLD_CONTRACT,
                                Placeholder.unparsed("region", noContract.regionId())));
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
        CommandSender sender = ctx.sender().source();
        int maxExtensions = ctx.get("maxextensions");
        WorldGuardRegion region = ctx.<WorldGuardRegion>optional("region")
                .orElseGet(() -> sender instanceof Player player
                        ? WorldGuardRegionResolver.resolveAtLocation(player.getLocation()) : null);
        if (region == null) {
            sender.sendMessage(messages.messageFor(MessageKeys.ERROR_NO_REGION));
            return;
        }
        String regionId = region.region().getId();
        UUID worldId = region.world().getUID();
        authorizeLeaseholdSet(sender, region, "realty.command.set.maxextensions.others",
                "realty.command.set.maxextensions.leasehold", LandlordGate.MANAGES, _ ->
        api.setMaxRenewals(regionId, worldId, maxExtensions).thenAccept(result -> {
            switch (result) {
                case RealtyBackend.SetMaxRenewalsResult.Success ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.SET_MAX_EXTENSIONS_SUCCESS,
                                Placeholder.unparsed("maxextensions",
                                        maxExtensions < 0 ? "unlimited" : String.valueOf(maxExtensions)),
                                Placeholder.unparsed("region", regionId)));
                case RealtyBackend.SetMaxRenewalsResult.NoLeaseholdContract ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.SET_MAX_EXTENSIONS_NO_LEASEHOLD_CONTRACT,
                                Placeholder.unparsed("region", regionId)));
                case RealtyBackend.SetMaxRenewalsResult.BelowCurrentExtensions(int current) ->
                        sender.sendMessage(messages.messageFor(MessageKeys.SET_MAX_EXTENSIONS_BELOW_CURRENT,
                                Placeholder.unparsed("current", String.valueOf(current)),
                                Placeholder.unparsed("region", regionId)));
                case RealtyBackend.SetMaxRenewalsResult.UpdateFailed ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.SET_MAX_EXTENSIONS_UPDATE_FAILED,
                                Placeholder.unparsed("region", regionId)));
            }
        }));
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
