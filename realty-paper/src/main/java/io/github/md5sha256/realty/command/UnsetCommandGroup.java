package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.api.RegionProfileService;
import io.github.md5sha256.realty.api.RegionState;
import io.github.md5sha256.realty.api.SignTextApplicator;
import io.github.md5sha256.realty.command.util.WorldGuardRegion;
import io.github.md5sha256.realty.command.util.WorldGuardRegionResolver;
import io.github.md5sha256.realty.api.RealtyApi;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.localisation.MessageKeys;
import io.github.md5sha256.realty.util.ExecutorState;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.incendo.cloud.paper.util.sender.Source;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Groups all unset-related subcommands under {@code /realty unset}.
 *
 * <ul>
 *   <li>{@code /realty unset price [region]} — clear freehold price</li>
 *   <li>{@code /realty unset titleholder [region]} — clear freehold title holder</li>
 *   <li>{@code /realty unset tenant [region]} — clear leasehold tenant</li>
 * </ul>
 */
public record UnsetCommandGroup(
        @NotNull RegionProfileService regionProfileService,
        @NotNull SignTextApplicator signTextApplicator,
        @NotNull ExecutorState executorState,
        @NotNull RealtyApi logic,
        @NotNull MessageContainer messages
) implements CustomCommandBean {

    @Override
    public @NotNull List<Command<Source>> commands(@NotNull Command.Builder<Source> builder) {
        var base = builder
                .literal("unset");
        return List.of(
                base.literal("price")
                        .permission("realty.command.unset.price")
                        .optional("region", WorldGuardRegionResolver.worldGuardRegionResolver())
                        .handler(this::executeUnsetPrice)
                        .build(),
                base.literal("titleholder")
                        .permission("realty.command.unset.titleholder")
                        .optional("region", WorldGuardRegionResolver.worldGuardRegionResolver())
                        .handler(this::executeUnsetTitleHolder)
                        .build(),
                base.literal("tenant")
                        .permission("realty.command.unset.tenant")
                        .optional("region", WorldGuardRegionResolver.worldGuardRegionResolver())
                        .handler(this::executeUnsetTenant)
                        .build()
        );
    }

    private void executeUnsetPrice(@NotNull CommandContext<Source> ctx) {
        CommandSender sender = ctx.sender().source();
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
                && !sender.hasPermission("realty.command.unset.price.others")
                && !region.region().getOwners().contains(player.getUniqueId())) {
            sender.sendMessage(messages.messageFor(MessageKeys.UNSET_NO_PERMISSION));
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                RealtyApi.UnsetPriceResult result = logic.unsetPrice(
                        regionId, worldId);
                switch (result) {
                    case RealtyApi.UnsetPriceResult.Success ignored ->
                            sender.sendMessage(messages.messageFor(MessageKeys.UNSET_PRICE_SUCCESS,
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyApi.UnsetPriceResult.NoFreeholdContract ignored ->
                            sender.sendMessage(messages.messageFor(MessageKeys.UNSET_PRICE_NO_FREEHOLD_CONTRACT,
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyApi.UnsetPriceResult.OfferPaymentInProgress ignored ->
                            sender.sendMessage(messages.messageFor(MessageKeys.UNSET_PRICE_OFFER_PAYMENT_IN_PROGRESS,
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyApi.UnsetPriceResult.BidPaymentInProgress ignored ->
                            sender.sendMessage(messages.messageFor(MessageKeys.UNSET_PRICE_BID_PAYMENT_IN_PROGRESS,
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyApi.UnsetPriceResult.UpdateFailed ignored ->
                            sender.sendMessage(messages.messageFor(MessageKeys.UNSET_PRICE_UPDATE_FAILED,
                                    Placeholder.unparsed("region", regionId)));
                }
            } catch (Exception ex) {
                sender.sendMessage(messages.messageFor(MessageKeys.UNSET_PRICE_ERROR,
                        Placeholder.unparsed("error", ex.getMessage())));
            }
        }, executorState.dbExec());
    }

    private void executeUnsetTitleHolder(@NotNull CommandContext<Source> ctx) {
        CommandSender sender = ctx.sender().source();
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
                && !sender.hasPermission("realty.command.unset.titleholder.others")
                && !region.region().getOwners().contains(player.getUniqueId())) {
            sender.sendMessage(messages.messageFor(MessageKeys.UNSET_NO_PERMISSION));
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                RealtyApi.SetTitleHolderResult result = logic.setTitleHolder(
                        regionId, worldId, null);
                switch (result) {
                    case RealtyApi.SetTitleHolderResult.Success(UUID previousTitleHolder) -> {
                            Map<String, String> placeholders = logic.getRegionPlaceholders(regionId, worldId);
                            executorState.mainThreadExec().execute(() -> {
                                    region.region().getOwners().clear();
                                    region.region().getMembers().clear();
                                    regionProfileService.applyFlags(region, RegionState.FOR_SALE, placeholders);
                                    signTextApplicator.updateLoadedSigns(region.world(), regionId, RegionState.FOR_SALE, placeholders);
                            });
                            sender.sendMessage(messages.messageFor(MessageKeys.UNSET_TITLEHOLDER_SUCCESS,
                                    Placeholder.unparsed("region", regionId)));
                    }
                    case RealtyApi.SetTitleHolderResult.NoFreeholdContract ignored ->
                            sender.sendMessage(messages.messageFor(MessageKeys.UNSET_TITLEHOLDER_NO_FREEHOLD_CONTRACT,
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyApi.SetTitleHolderResult.UpdateFailed ignored ->
                            sender.sendMessage(messages.messageFor(MessageKeys.UNSET_TITLEHOLDER_UPDATE_FAILED,
                                    Placeholder.unparsed("region", regionId)));
                }
            } catch (Exception ex) {
                sender.sendMessage(messages.messageFor(MessageKeys.UNSET_TITLEHOLDER_ERROR,
                        Placeholder.unparsed("error", ex.getMessage())));
            }
        }, executorState.dbExec());
    }

    private void executeUnsetTenant(@NotNull CommandContext<Source> ctx) {
        CommandSender sender = ctx.sender().source();
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
                && !sender.hasPermission("realty.command.unset.tenant.others")
                && !region.region().getOwners().contains(player.getUniqueId())) {
            sender.sendMessage(messages.messageFor(MessageKeys.UNSET_NO_PERMISSION));
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                RealtyApi.SetTenantResult result = logic.setTenant(
                        regionId, worldId, null);
                switch (result) {
                    case RealtyApi.SetTenantResult.Success(UUID previousTenant, UUID ignored2) -> {
                            Map<String, String> placeholders = logic.getRegionPlaceholders(regionId, worldId);
                            executorState.mainThreadExec().execute(() -> {
                                    region.region().getOwners().clear();
                                    region.region().getMembers().clear();
                                    regionProfileService.applyFlags(region, RegionState.FOR_LEASE, placeholders);
                                    signTextApplicator.updateLoadedSigns(region.world(), regionId, RegionState.FOR_LEASE, placeholders);
                            });
                            sender.sendMessage(messages.messageFor(MessageKeys.UNSET_TENANT_SUCCESS,
                                    Placeholder.unparsed("region", regionId)));
                    }
                    case RealtyApi.SetTenantResult.NoLeaseholdContract ignored ->
                            sender.sendMessage(messages.messageFor(MessageKeys.UNSET_TENANT_NO_LEASEHOLD_CONTRACT,
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyApi.SetTenantResult.UpdateFailed ignored ->
                            sender.sendMessage(messages.messageFor(MessageKeys.UNSET_TENANT_UPDATE_FAILED,
                                    Placeholder.unparsed("region", regionId)));
                }
            } catch (Exception ex) {
                sender.sendMessage(messages.messageFor(MessageKeys.UNSET_TENANT_ERROR,
                        Placeholder.unparsed("error", ex.getMessage())));
            }
        }, executorState.dbExec());
    }

}
