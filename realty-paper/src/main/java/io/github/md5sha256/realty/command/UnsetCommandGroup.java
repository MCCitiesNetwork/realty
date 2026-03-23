package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.api.RegionProfileService;
import io.github.md5sha256.realty.api.RegionState;
import io.github.md5sha256.realty.api.SignTextApplicator;
import io.github.md5sha256.realty.command.util.WorldGuardRegion;
import io.github.md5sha256.realty.command.util.WorldGuardRegionResolver;
import io.github.md5sha256.realty.database.RealtyLogicImpl;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.localisation.MessageKeys;
import io.github.md5sha256.realty.util.ExecutorState;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

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
 *   <li>{@code /realty unset tenant [region]} — clear lease tenant</li>
 * </ul>
 */
public record UnsetCommandGroup(
        @NotNull RegionProfileService regionProfileService,
        @NotNull SignTextApplicator signTextApplicator,
        @NotNull ExecutorState executorState,
        @NotNull RealtyLogicImpl logic,
        @NotNull MessageContainer messages
) implements CustomCommandBean {

    @Override
    public @NotNull List<Command<CommandSourceStack>> commands(@NotNull Command.Builder<CommandSourceStack> builder) {
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

    private void executeUnsetPrice(@NotNull CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.sender().getSender() instanceof Player sender)) {
            return;
        }
        WorldGuardRegion region = ctx.<WorldGuardRegion>optional("region")
                .orElseGet(() -> WorldGuardRegionResolver.resolveAtLocation(sender.getLocation()));
        if (region == null) {
            sender.sendMessage(messages.messageFor(MessageKeys.ERROR_NO_REGION));
            return;
        }
        String regionId = region.region().getId();
        UUID worldId = region.world().getUID();
        UUID playerId = sender.getUniqueId();
        boolean canUnsetOthers = sender.hasPermission("realty.command.unset.price.others");
        CompletableFuture.supplyAsync(() -> {
            if (canUnsetOthers) {
                return true;
            }
            try {
                return logic.checkRegionAuthority(regionId, worldId, playerId);
            } catch (Exception ex) {
                ex.printStackTrace();
                sender.sendMessage(messages.messageFor(MessageKeys.UNSET_CHECK_PERMISSIONS_ERROR,
                        Placeholder.unparsed("error", ex.getMessage())));
                return false;
            }
        }, executorState.dbExec()).thenAcceptAsync(allowed -> {
            if (!allowed) {
                sender.sendMessage(messages.messageFor(MessageKeys.UNSET_NO_PERMISSION));
                return;
            }
            try {
                RealtyLogicImpl.UnsetPriceResult result = logic.unsetPrice(
                        regionId, worldId);
                switch (result) {
                    case RealtyLogicImpl.UnsetPriceResult.Success ignored ->
                            sender.sendMessage(messages.messageFor(MessageKeys.UNSET_PRICE_SUCCESS,
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyLogicImpl.UnsetPriceResult.NoFreeholdContract ignored ->
                            sender.sendMessage(messages.messageFor(MessageKeys.UNSET_PRICE_NO_FREEHOLD_CONTRACT,
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyLogicImpl.UnsetPriceResult.OfferPaymentInProgress ignored ->
                            sender.sendMessage(messages.messageFor(MessageKeys.UNSET_PRICE_OFFER_PAYMENT_IN_PROGRESS,
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyLogicImpl.UnsetPriceResult.BidPaymentInProgress ignored ->
                            sender.sendMessage(messages.messageFor(MessageKeys.UNSET_PRICE_BID_PAYMENT_IN_PROGRESS,
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyLogicImpl.UnsetPriceResult.UpdateFailed ignored ->
                            sender.sendMessage(messages.messageFor(MessageKeys.UNSET_PRICE_UPDATE_FAILED,
                                    Placeholder.unparsed("region", regionId)));
                }
            } catch (Exception ex) {
                sender.sendMessage(messages.messageFor(MessageKeys.UNSET_PRICE_ERROR,
                        Placeholder.unparsed("error", ex.getMessage())));
            }
        }, executorState.dbExec());
    }

    private void executeUnsetTitleHolder(@NotNull CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.sender().getSender() instanceof Player sender)) {
            return;
        }
        WorldGuardRegion region = ctx.<WorldGuardRegion>optional("region")
                .orElseGet(() -> WorldGuardRegionResolver.resolveAtLocation(sender.getLocation()));
        if (region == null) {
            sender.sendMessage(messages.messageFor(MessageKeys.ERROR_NO_REGION));
            return;
        }
        String regionId = region.region().getId();
        UUID worldId = region.world().getUID();
        UUID playerId = sender.getUniqueId();
        boolean canUnsetOthers = sender.hasPermission("realty.command.unset.titleholder.others");
        CompletableFuture.supplyAsync(() -> {
            if (canUnsetOthers) {
                return true;
            }
            try {
                return logic.checkRegionAuthority(regionId, worldId, playerId);
            } catch (Exception ex) {
                ex.printStackTrace();
                sender.sendMessage(messages.messageFor(MessageKeys.UNSET_CHECK_PERMISSIONS_ERROR,
                        Placeholder.unparsed("error", ex.getMessage())));
                return false;
            }
        }, executorState.dbExec()).thenAcceptAsync(allowed -> {
            if (!allowed) {
                sender.sendMessage(messages.messageFor(MessageKeys.UNSET_NO_PERMISSION));
                return;
            }
            try {
                RealtyLogicImpl.SetTitleHolderResult result = logic.setTitleHolder(
                        regionId, worldId, null);
                switch (result) {
                    case RealtyLogicImpl.SetTitleHolderResult.Success(UUID previousTitleHolder) -> {
                            Map<String, String> placeholders = logic.getRegionPlaceholders(regionId, worldId);
                            executorState.mainThreadExec().execute(() -> {
                                    if (previousTitleHolder != null) {
                                        region.region().getMembers().removePlayer(previousTitleHolder);
                                    }
                                    regionProfileService.applyFlags(region, RegionState.FOR_SALE, placeholders);
                                    signTextApplicator.updateLoadedSigns(region.world(), regionId, RegionState.FOR_SALE, placeholders);
                            });
                            sender.sendMessage(messages.messageFor(MessageKeys.UNSET_TITLEHOLDER_SUCCESS,
                                    Placeholder.unparsed("region", regionId)));
                    }
                    case RealtyLogicImpl.SetTitleHolderResult.NoFreeholdContract ignored ->
                            sender.sendMessage(messages.messageFor(MessageKeys.UNSET_TITLEHOLDER_NO_FREEHOLD_CONTRACT,
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyLogicImpl.SetTitleHolderResult.UpdateFailed ignored ->
                            sender.sendMessage(messages.messageFor(MessageKeys.UNSET_TITLEHOLDER_UPDATE_FAILED,
                                    Placeholder.unparsed("region", regionId)));
                }
            } catch (Exception ex) {
                sender.sendMessage(messages.messageFor(MessageKeys.UNSET_TITLEHOLDER_ERROR,
                        Placeholder.unparsed("error", ex.getMessage())));
            }
        }, executorState.dbExec());
    }

    private void executeUnsetTenant(@NotNull CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.sender().getSender() instanceof Player sender)) {
            return;
        }
        WorldGuardRegion region = ctx.<WorldGuardRegion>optional("region")
                .orElseGet(() -> WorldGuardRegionResolver.resolveAtLocation(sender.getLocation()));
        if (region == null) {
            sender.sendMessage(messages.messageFor(MessageKeys.ERROR_NO_REGION));
            return;
        }
        String regionId = region.region().getId();
        UUID worldId = region.world().getUID();
        UUID playerId = sender.getUniqueId();
        boolean canUnsetOthers = sender.hasPermission("realty.command.unset.tenant.others");
        CompletableFuture.supplyAsync(() -> {
            if (canUnsetOthers) {
                return true;
            }
            try {
                return logic.checkRegionAuthority(regionId, worldId, playerId);
            } catch (Exception ex) {
                ex.printStackTrace();
                sender.sendMessage(messages.messageFor(MessageKeys.UNSET_CHECK_PERMISSIONS_ERROR,
                        Placeholder.unparsed("error", ex.getMessage())));
                return false;
            }
        }, executorState.dbExec()).thenAcceptAsync(allowed -> {
            if (!allowed) {
                sender.sendMessage(messages.messageFor(MessageKeys.UNSET_NO_PERMISSION));
                return;
            }
            try {
                RealtyLogicImpl.SetTenantResult result = logic.setTenant(
                        regionId, worldId, null);
                switch (result) {
                    case RealtyLogicImpl.SetTenantResult.Success(UUID previousTenant) -> {
                            Map<String, String> placeholders = logic.getRegionPlaceholders(regionId, worldId);
                            executorState.mainThreadExec().execute(() -> {
                                    if (previousTenant != null) {
                                        region.region().getMembers().removePlayer(previousTenant);
                                    }
                                    regionProfileService.applyFlags(region, RegionState.FOR_LEASE, placeholders);
                                    signTextApplicator.updateLoadedSigns(region.world(), regionId, RegionState.FOR_LEASE, placeholders);
                            });
                            sender.sendMessage(messages.messageFor(MessageKeys.UNSET_TENANT_SUCCESS,
                                    Placeholder.unparsed("region", regionId)));
                    }
                    case RealtyLogicImpl.SetTenantResult.NoLeaseContract ignored ->
                            sender.sendMessage(messages.messageFor(MessageKeys.UNSET_TENANT_NO_LEASE_CONTRACT,
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyLogicImpl.SetTenantResult.UpdateFailed ignored ->
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
