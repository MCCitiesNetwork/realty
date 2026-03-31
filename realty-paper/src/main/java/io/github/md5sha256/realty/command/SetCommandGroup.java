package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.api.CurrencyFormatter;
import io.github.md5sha256.realty.api.RegionProfileService;
import io.github.md5sha256.realty.api.RegionState;
import io.github.md5sha256.realty.api.SignTextApplicator;
import io.github.md5sha256.realty.command.util.AuthorityParser;
import io.github.md5sha256.realty.command.util.DurationParser;
import io.github.md5sha256.realty.command.util.SubregionLandlordUpdater;
import io.github.md5sha256.realty.command.util.WorldGuardRegion;
import io.github.md5sha256.realty.command.util.WorldGuardRegionParser;
import io.github.md5sha256.realty.command.util.WorldGuardRegionResolver;
import io.github.md5sha256.realty.api.RealtyApi;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.localisation.MessageKeys;
import io.github.md5sha256.realty.util.ExecutorState;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.incendo.cloud.paper.util.sender.Source;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.DoubleParser;
import org.incendo.cloud.parser.standard.IntegerParser;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Groups all set-related subcommands under {@code /realty set}.
 *
 * <ul>
 *   <li>{@code /realty set price <price> <region>} — set freehold price</li>
 *   <li>{@code /realty set duration <duration> <region>} — set leasehold duration</li>
 *   <li>{@code /realty set landlord <player> <region>} — set leasehold landlord</li>
 *   <li>{@code /realty set titleholder <player> <region>} — set freehold title holder</li>
 *   <li>{@code /realty set tenant <player> <region>} — set leasehold tenant</li>
 *   <li>{@code /realty set maxextensions <count> <region>} — set leasehold max extensions (-1 for unlimited)</li>
 * </ul>
 */
public record SetCommandGroup(
        @NotNull ExecutorState executorState,
        @NotNull RealtyApi logic,
        @NotNull RegionProfileService regionProfileService,
        @NotNull SignTextApplicator signTextApplicator,
        @NotNull MessageContainer messages
) implements CustomCommandBean {

    private static @NotNull String resolveName(@NotNull UUID uuid) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        String name = player.getName();
        return name != null ? name : uuid.toString();
    }

    @Override
    public @NotNull List<Command<Source>> commands(@NotNull Command.Builder<Source> builder) {
        var base = builder
                .literal("set");
        return List.of(
                base.literal("price")
                        .permission("realty.command.set.price")
                        .required("price", DoubleParser.doubleParser(0, Double.MAX_VALUE))
                        .optional("region", WorldGuardRegionResolver.worldGuardRegionResolver())
                        .handler(this::executeSetPrice)
                        .build(),
                base.literal("duration")
                        .permission("realty.command.set.duration")
                        .required("duration", DurationParser.duration())
                        .optional("region", WorldGuardRegionResolver.worldGuardRegionResolver())
                        .handler(this::executeSetDuration)
                        .build(),
                base.literal("landlord")
                        .permission("realty.command.set.landlord")
                        .required("landlord", AuthorityParser.authority())
                        .optional("region", WorldGuardRegionResolver.worldGuardRegionResolver())
                        .handler(this::executeSetLandlord)
                        .build(),
                base.literal("titleholder")
                        .permission("realty.command.set.titleholder")
                        .required("titleholder", AuthorityParser.authority())
                        .optional("region", WorldGuardRegionResolver.worldGuardRegionResolver())
                        .handler(this::executeSetTitleHolder)
                        .build(),
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
                && !sender.hasPermission("realty.command.set.price.others")
                && !region.region().getOwners().contains(player.getUniqueId())) {
            sender.sendMessage(messages.messageFor(MessageKeys.SET_NO_PERMISSION));
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                RealtyApi.SetPriceResult result = logic.setPrice(
                        regionId, worldId, price);
                switch (result) {
                    case RealtyApi.SetPriceResult.Success ignored ->
                            sender.sendMessage(messages.messageFor(MessageKeys.SET_PRICE_SUCCESS,
                                    Placeholder.unparsed("price", CurrencyFormatter.format(price)),
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyApi.SetPriceResult.NoFreeholdContract ignored ->
                            sender.sendMessage(messages.messageFor(MessageKeys.SET_PRICE_NO_FREEHOLD_CONTRACT,
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyApi.SetPriceResult.AuctionExists ignored ->
                            sender.sendMessage(messages.messageFor(MessageKeys.SET_PRICE_AUCTION_EXISTS,
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyApi.SetPriceResult.OfferPaymentInProgress ignored ->
                            sender.sendMessage(messages.messageFor(MessageKeys.SET_PRICE_OFFER_PAYMENT_IN_PROGRESS,
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyApi.SetPriceResult.BidPaymentInProgress ignored ->
                            sender.sendMessage(messages.messageFor(MessageKeys.SET_PRICE_BID_PAYMENT_IN_PROGRESS,
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyApi.SetPriceResult.UpdateFailed ignored ->
                            sender.sendMessage(messages.messageFor(MessageKeys.SET_PRICE_UPDATE_FAILED,
                                    Placeholder.unparsed("region", regionId)));
                }
            } catch (Exception ex) {
                sender.sendMessage(messages.messageFor(MessageKeys.SET_PRICE_ERROR,
                        Placeholder.unparsed("error", ex.getMessage())));
            }
        }, executorState.dbExec());
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
        if (sender instanceof Player player
                && !sender.hasPermission("realty.command.set.duration.others")
                && !region.region().getOwners().contains(player.getUniqueId())) {
            sender.sendMessage(messages.messageFor(MessageKeys.SET_NO_PERMISSION));
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                RealtyApi.SetDurationResult result = logic.setDuration(
                        regionId, worldId, duration.toSeconds());
                switch (result) {
                    case RealtyApi.SetDurationResult.Success ignored ->
                            sender.sendMessage(messages.messageFor(MessageKeys.SET_DURATION_SUCCESS,
                                    Placeholder.unparsed("duration", duration.toString()),
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyApi.SetDurationResult.NoLeaseholdContract ignored ->
                            sender.sendMessage(messages.messageFor(MessageKeys.SET_DURATION_NO_LEASEHOLD_CONTRACT,
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyApi.SetDurationResult.UpdateFailed ignored ->
                            sender.sendMessage(messages.messageFor(MessageKeys.SET_DURATION_UPDATE_FAILED,
                                    Placeholder.unparsed("region", regionId)));
                }
            } catch (Exception ex) {
                sender.sendMessage(messages.messageFor(MessageKeys.SET_DURATION_ERROR,
                        Placeholder.unparsed("error", ex.getMessage())));
            }
        }, executorState.dbExec());
    }

    private void executeSetLandlord(@NotNull CommandContext<Source> ctx) {
        CommandSender sender = ctx.sender().source();
        UUID landlordId = ctx.get("landlord");
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
                && !sender.hasPermission("realty.command.set.landlord.others")
                && !region.region().getOwners().contains(player.getUniqueId())) {
            sender.sendMessage(messages.messageFor(MessageKeys.SET_NO_PERMISSION));
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                RealtyApi.SetLandlordResult result = logic.setLandlord(
                        regionId, worldId, landlordId);
                switch (result) {
                    case RealtyApi.SetLandlordResult.Success(UUID previousLandlord) -> {
                        executorState.mainThreadExec().execute(() -> {
                            region.region().getMembers().clear();
                        });
                        sender.sendMessage(messages.messageFor(MessageKeys.SET_LANDLORD_SUCCESS,
                                Placeholder.unparsed("landlord", resolveName(landlordId)),
                                Placeholder.unparsed("region", regionId)));
                    }
                    case RealtyApi.SetLandlordResult.NoLeaseholdContract ignored ->
                            sender.sendMessage(messages.messageFor(MessageKeys.SET_LANDLORD_NO_LEASEHOLD_CONTRACT,
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyApi.SetLandlordResult.UpdateFailed ignored ->
                            sender.sendMessage(messages.messageFor(MessageKeys.SET_LANDLORD_UPDATE_FAILED,
                                    Placeholder.unparsed("region", regionId)));
                }
            } catch (Exception ex) {
                sender.sendMessage(messages.messageFor(MessageKeys.SET_LANDLORD_ERROR,
                        Placeholder.unparsed("error", ex.getMessage())));
            }
        }, executorState.dbExec());
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
        String regionId = region.region().getId();
        UUID worldId = region.world().getUID();
        if (sender instanceof Player player
                && !sender.hasPermission("realty.command.set.titleholder.others")
                && !region.region().getOwners().contains(player.getUniqueId())) {
            sender.sendMessage(messages.messageFor(MessageKeys.SET_NO_PERMISSION));
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                RealtyApi.SetTitleHolderResult result = logic.setTitleHolder(
                        regionId, worldId, titleHolderId);
                switch (result) {
                    case RealtyApi.SetTitleHolderResult.Success(UUID previousTitleHolder) -> {
                        Map<String, String> placeholders = logic.getRegionPlaceholders(regionId,
                                worldId);
                        executorState.mainThreadExec().execute(() -> {
                            com.sk89q.worldguard.protection.regions.ProtectedRegion protectedRegion =
                                    region.region();
                            protectedRegion.getOwners().clear();
                            protectedRegion.getMembers().clear();
                            protectedRegion.getOwners().addPlayer(titleHolderId);
                            regionProfileService.applyFlags(region, RegionState.SOLD, placeholders);
                            signTextApplicator.updateLoadedSigns(region.world(),
                                    regionId,
                                    RegionState.SOLD,
                                    placeholders);
                            SubregionLandlordUpdater.updateChildLandlords(
                                    regionId,
                                    region.world(),
                                    titleHolderId,
                                    logic,
                                    executorState);
                        });
                        sender.sendMessage(messages.messageFor(MessageKeys.SET_TITLEHOLDER_SUCCESS,
                                Placeholder.unparsed("titleholder", resolveName(titleHolderId)),
                                Placeholder.unparsed("region", regionId)));
                    }
                    case RealtyApi.SetTitleHolderResult.NoFreeholdContract ignored ->
                            sender.sendMessage(messages.messageFor(MessageKeys.SET_TITLEHOLDER_NO_FREEHOLD_CONTRACT,
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyApi.SetTitleHolderResult.UpdateFailed ignored ->
                            sender.sendMessage(messages.messageFor(MessageKeys.SET_TITLEHOLDER_UPDATE_FAILED,
                                    Placeholder.unparsed("region", regionId)));
                }
            } catch (Exception ex) {
                sender.sendMessage(messages.messageFor(MessageKeys.SET_TITLEHOLDER_ERROR,
                        Placeholder.unparsed("error", ex.getMessage())));
            }
        }, executorState.dbExec());
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
        String regionId = region.region().getId();
        UUID worldId = region.world().getUID();
        if (sender instanceof Player player
                && !sender.hasPermission("realty.command.set.tenant.others")
                && !region.region().getOwners().contains(player.getUniqueId())) {
            sender.sendMessage(messages.messageFor(MessageKeys.SET_NO_PERMISSION));
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                RealtyApi.SetTenantResult result = logic.setTenant(
                        regionId, worldId, tenantId);
                switch (result) {
                    case RealtyApi.SetTenantResult.Success(UUID previousTenant, UUID ignored2) -> {
                        Map<String, String> placeholders = logic.getRegionPlaceholders(regionId,
                                worldId);
                        executorState.mainThreadExec().execute(() -> {
                            com.sk89q.worldguard.protection.regions.ProtectedRegion protectedRegion =
                                    region.region();
                            protectedRegion.getOwners().clear();
                            protectedRegion.getMembers().clear();
                            protectedRegion.getOwners().addPlayer(tenantId);
                            regionProfileService.applyFlags(region,
                                    RegionState.LEASED,
                                    placeholders);
                            signTextApplicator.updateLoadedSigns(region.world(),
                                    regionId,
                                    RegionState.LEASED,
                                    placeholders);
                        });
                        sender.sendMessage(messages.messageFor(MessageKeys.SET_TENANT_SUCCESS,
                                Placeholder.unparsed("tenant", resolveName(tenantId)),
                                Placeholder.unparsed("region", regionId)));
                    }
                    case RealtyApi.SetTenantResult.NoLeaseholdContract ignored ->
                            sender.sendMessage(messages.messageFor(MessageKeys.SET_TENANT_NO_LEASEHOLD_CONTRACT,
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyApi.SetTenantResult.UpdateFailed ignored ->
                            sender.sendMessage(messages.messageFor(MessageKeys.SET_TENANT_UPDATE_FAILED,
                                    Placeholder.unparsed("region", regionId)));
                }
            } catch (Exception ex) {
                sender.sendMessage(messages.messageFor(MessageKeys.SET_TENANT_ERROR,
                        Placeholder.unparsed("error", ex.getMessage())));
            }
        }, executorState.dbExec());
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
        if (sender instanceof Player player
                && !sender.hasPermission("realty.command.set.maxextensions.others")
                && !region.region().getOwners().contains(player.getUniqueId())) {
            sender.sendMessage(messages.messageFor(MessageKeys.SET_NO_PERMISSION));
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                RealtyApi.SetMaxRenewalsResult result = logic.setMaxRenewals(
                        regionId, worldId, maxExtensions);
                switch (result) {
                    case RealtyApi.SetMaxRenewalsResult.Success ignored ->
                            sender.sendMessage(messages.messageFor(MessageKeys.SET_MAX_EXTENSIONS_SUCCESS,
                                    Placeholder.unparsed("maxextensions",
                                            maxExtensions < 0 ? "unlimited" : String.valueOf(maxExtensions)),
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyApi.SetMaxRenewalsResult.NoLeaseholdContract ignored ->
                            sender.sendMessage(messages.messageFor(MessageKeys.SET_MAX_EXTENSIONS_NO_LEASEHOLD_CONTRACT,
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyApi.SetMaxRenewalsResult.BelowCurrentExtensions(int current) ->
                            sender.sendMessage(messages.messageFor(MessageKeys.SET_MAX_EXTENSIONS_BELOW_CURRENT,
                                    Placeholder.unparsed("current", String.valueOf(current)),
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyApi.SetMaxRenewalsResult.UpdateFailed ignored ->
                            sender.sendMessage(messages.messageFor(MessageKeys.SET_MAX_EXTENSIONS_UPDATE_FAILED,
                                    Placeholder.unparsed("region", regionId)));
                }
            } catch (Exception ex) {
                sender.sendMessage(messages.messageFor(MessageKeys.SET_MAX_EXTENSIONS_ERROR,
                        Placeholder.unparsed("error", ex.getMessage())));
            }
        }, executorState.dbExec());
    }

}
