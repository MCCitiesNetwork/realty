package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.api.RealtyBackend;
import io.github.md5sha256.realty.api.RealtyPaperApi;
import io.github.md5sha256.realty.api.WorldGuardRegion;
import io.github.md5sha256.realty.command.util.RegionOrFlagParser;
import io.github.md5sha256.realty.command.util.SetRouter;
import io.github.md5sha256.realty.command.util.SetRouting;
import io.github.md5sha256.realty.command.util.WorldGuardRegionResolver;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.localisation.MessageKeys;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.paper.util.sender.Source;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Groups all unset-related subcommands under {@code /realty unset}.
 *
 * <ul>
 *   <li>{@code /realty unset price [region] [--now]} — clear freehold price</li>
 *   <li>{@code /realty unset titleholder [region] [--now]} — clear freehold title holder</li>
 *   <li>{@code /realty unset tenant [region] [--now]} — clear leasehold tenant</li>
 * </ul>
 *
 * <p>These follow the holder rule {@link SetRouter} applies to {@code /realty set landlord},
 * {@code tenant} and {@code titleholder}: the change is made by whoever holds the region (the landlord of
 * a lease; the title holder, or the authority while there is none, of a freehold), never by the tenant
 * of a lease. On a rented region it is made only with {@code --now}, which needs
 * {@code realty.command.set.now}; on a vacant or freehold region it is made at once. Each command's
 * {@code .others} node lets its holder test be bypassed.</p>
 */
public record UnsetCommandGroup(
        @NotNull RealtyPaperApi api,
        @NotNull MessageContainer messages,
        @NotNull SetRouter router
) implements CustomCommandBean {

    @Override
    public @NotNull List<Command<? extends Source>> commands(@NotNull Command.Builder<Source> builder) {
        var base = builder
                .literal("unset");
        return List.of(
                base.literal("price")
                        .permission("realty.command.unset.price")
                        .optional("region", RegionOrFlagParser.regionOrFlag())
                        .flag(SetCommandGroup.NOW_FLAG)
                        .handler(this::executeUnsetPrice)
                        .build(),
                base.literal("titleholder")
                        .permission("realty.command.unset.titleholder")
                        .optional("region", RegionOrFlagParser.regionOrFlag())
                        .flag(SetCommandGroup.NOW_FLAG)
                        .handler(this::executeUnsetTitleHolder)
                        .build(),
                base.literal("tenant")
                        .permission("realty.command.unset.tenant")
                        .optional("region", RegionOrFlagParser.regionOrFlag())
                        .flag(SetCommandGroup.NOW_FLAG)
                        .handler(this::executeUnsetTenant)
                        .build()
        );
    }

    /** The region the command names, or the one the player stands in; null when there is neither. */
    private static @Nullable WorldGuardRegion regionOf(@NotNull CommandContext<Source> ctx,
                                                       @NotNull CommandSender sender) {
        return ctx.<Optional<WorldGuardRegion>>optional("region")
                .flatMap(Function.identity())
                .orElseGet(() -> sender instanceof Player player
                        ? WorldGuardRegionResolver.resolveAtLocation(player.getLocation()) : null);
    }

    private void executeUnsetPrice(@NotNull CommandContext<Source> ctx) {
        CommandSender sender = ctx.sender().source();
        WorldGuardRegion region = regionOf(ctx, sender);
        if (region == null) {
            sender.sendMessage(messages.messageFor(MessageKeys.ERROR_NO_REGION));
            return;
        }
        String regionId = region.region().getId();
        UUID worldId = region.world().getUID();
        router.route(sender, region, SetRouting.Kind.HOLDER, SetRouter.HolderTest.MANAGES,
                "realty.command.unset.price.others", MessageKeys.UNSET_NO_PERMISSION,
                ctx.flags().hasFlag(SetCommandGroup.NOW_FLAG), false, routed ->
        router.reportWriteFailure(api.unsetPrice(regionId, worldId, routed.actor()).thenAccept(result -> {
            switch (result) {
                case RealtyBackend.UnsetPriceResult.Success ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.UNSET_PRICE_SUCCESS,
                                Placeholder.unparsed("region", regionId)));
                case RealtyBackend.UnsetPriceResult.NoFreeholdContract ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.UNSET_PRICE_NO_FREEHOLD_CONTRACT,
                                Placeholder.unparsed("region", regionId)));
                case RealtyBackend.UnsetPriceResult.OfferPaymentInProgress ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.UNSET_PRICE_OFFER_PAYMENT_IN_PROGRESS,
                                Placeholder.unparsed("region", regionId)));
                case RealtyBackend.UnsetPriceResult.BidPaymentInProgress ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.UNSET_PRICE_BID_PAYMENT_IN_PROGRESS,
                                Placeholder.unparsed("region", regionId)));
                case RealtyBackend.UnsetPriceResult.NotAuthorized ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.UNSET_NO_PERMISSION,
                                Placeholder.unparsed("region", regionId)));
                case RealtyBackend.UnsetPriceResult.UpdateFailed ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.UNSET_PRICE_UPDATE_FAILED,
                                Placeholder.unparsed("region", regionId)));
            }
        }), sender, MessageKeys.UNSET_PRICE_ERROR));
    }

    private void executeUnsetTitleHolder(@NotNull CommandContext<Source> ctx) {
        CommandSender sender = ctx.sender().source();
        WorldGuardRegion region = regionOf(ctx, sender);
        if (region == null) {
            sender.sendMessage(messages.messageFor(MessageKeys.ERROR_NO_REGION));
            return;
        }
        String regionId = region.region().getId();
        router.route(sender, region, SetRouting.Kind.HOLDER, SetRouter.HolderTest.MANAGES,
                "realty.command.unset.titleholder.others", MessageKeys.UNSET_NO_PERMISSION,
                ctx.flags().hasFlag(SetCommandGroup.NOW_FLAG), false, routed ->
        router.reportWriteFailure(api.setTitleHolder(region, (Party) null, routed.actor())
                .thenAccept(result -> {
            switch (result) {
                case RealtyPaperApi.SetTitleHolderResult.Success ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.UNSET_TITLEHOLDER_SUCCESS,
                                Placeholder.unparsed("region", regionId)));
                case RealtyPaperApi.SetTitleHolderResult.NoFreeholdContract ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.UNSET_TITLEHOLDER_NO_FREEHOLD_CONTRACT,
                                Placeholder.unparsed("region", regionId)));
                case RealtyPaperApi.SetTitleHolderResult.NotAuthorized ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.UNSET_NO_PERMISSION,
                                Placeholder.unparsed("region", regionId)));
                case RealtyPaperApi.SetTitleHolderResult.UpdateFailed ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.UNSET_TITLEHOLDER_UPDATE_FAILED,
                                Placeholder.unparsed("region", regionId)));
                case RealtyPaperApi.SetTitleHolderResult.Error error ->
                        sender.sendMessage(messages.messageFor(MessageKeys.UNSET_TITLEHOLDER_ERROR,
                                Placeholder.unparsed("error", error.message())));
            }
        }), sender, MessageKeys.UNSET_TITLEHOLDER_ERROR));
    }

    private void executeUnsetTenant(@NotNull CommandContext<Source> ctx) {
        CommandSender sender = ctx.sender().source();
        WorldGuardRegion region = regionOf(ctx, sender);
        if (region == null) {
            sender.sendMessage(messages.messageFor(MessageKeys.ERROR_NO_REGION));
            return;
        }
        String regionId = region.region().getId();
        router.route(sender, region, SetRouting.Kind.HOLDER, SetRouter.HolderTest.MANAGES,
                "realty.command.unset.tenant.others", MessageKeys.UNSET_NO_PERMISSION,
                ctx.flags().hasFlag(SetCommandGroup.NOW_FLAG), false, routed ->
        router.reportWriteFailure(api.setTenant(region, (Party) null, routed.actor(), routed.vacantOnly())
                .thenAccept(result -> {
            switch (result) {
                case RealtyPaperApi.SetTenantResult.Success ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.UNSET_TENANT_SUCCESS,
                                Placeholder.unparsed("region", regionId)));
                case RealtyPaperApi.SetTenantResult.NoLeaseholdContract ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.UNSET_TENANT_NO_LEASEHOLD_CONTRACT,
                                Placeholder.unparsed("region", regionId)));
                case RealtyPaperApi.SetTenantResult.NotAuthorized ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.SET_NOT_LANDLORD,
                                Placeholder.unparsed("region", regionId)));
                case RealtyPaperApi.SetTenantResult.Occupied ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.SET_JUST_RENTED,
                                Placeholder.unparsed("region", regionId)));
                case RealtyPaperApi.SetTenantResult.UpdateFailed ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.UNSET_TENANT_UPDATE_FAILED,
                                Placeholder.unparsed("region", regionId)));
                case RealtyPaperApi.SetTenantResult.Error error ->
                        sender.sendMessage(messages.messageFor(MessageKeys.UNSET_TENANT_ERROR,
                                Placeholder.unparsed("error", error.message())));
            }
        }), sender, MessageKeys.UNSET_TENANT_ERROR));
    }

}
