package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.api.RegionProfileService;
import io.github.md5sha256.realty.api.RegionState;
import io.github.md5sha256.realty.command.util.AuthorityParser;
import io.github.md5sha256.realty.command.util.DurationParser;
import io.github.md5sha256.realty.command.util.WorldGuardRegion;
import io.github.md5sha256.realty.command.util.WorldGuardRegionParser;
import io.github.md5sha256.realty.database.RealtyLogicImpl;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.util.ExecutorState;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.DoubleParser;
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
 *   <li>{@code /realty set duration <duration> <region>} — set lease duration</li>
 *   <li>{@code /realty set landlord <player> <region>} — set lease landlord</li>
 *   <li>{@code /realty set titleholder <player> <region>} — set freehold title holder</li>
 *   <li>{@code /realty set tenant <player> <region>} — set lease tenant</li>
 * </ul>
 */
public record SetCommandGroup(
        @NotNull ExecutorState executorState,
        @NotNull RealtyLogicImpl logic,
        @NotNull RegionProfileService regionProfileService,
        @NotNull MessageContainer messages
) implements CustomCommandBean {

    @Override
    public @NotNull List<Command<CommandSourceStack>> commands(@NotNull CommandManager<CommandSourceStack> manager) {
        var base = manager.commandBuilder("realty")
                .literal("set");
        return List.of(
                base.literal("price")
                        .permission("realty.command.set.price")
                        .required("price", DoubleParser.doubleParser(0, Double.MAX_VALUE))
                        .required("region", WorldGuardRegionParser.worldGuardRegion())
                        .handler(this::executeSetPrice)
                        .build(),
                base.literal("duration")
                        .permission("realty.command.set.duration")
                        .required("duration", DurationParser.duration())
                        .required("region", WorldGuardRegionParser.worldGuardRegion())
                        .handler(this::executeSetDuration)
                        .build(),
                base.literal("landlord")
                        .permission("realty.command.set.landlord")
                        .required("landlord", AuthorityParser.authority())
                        .required("region", WorldGuardRegionParser.worldGuardRegion())
                        .handler(this::executeSetLandlord)
                        .build(),
                base.literal("titleholder")
                        .permission("realty.command.set.titleholder")
                        .required("titleholder", AuthorityParser.authority())
                        .required("region", WorldGuardRegionParser.worldGuardRegion())
                        .handler(this::executeSetTitleHolder)
                        .build(),
                base.literal("tenant")
                        .permission("realty.command.set.tenant")
                        .required("tenant", AuthorityParser.authority())
                        .required("region", WorldGuardRegionParser.worldGuardRegion())
                        .handler(this::executeSetTenant)
                        .build()
        );
    }

    private void executeSetPrice(@NotNull CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.sender().getSender() instanceof Player sender)) {
            return;
        }
        double price = ctx.get("price");
        WorldGuardRegion region = ctx.get("region");
        String regionId = region.region().getId();
        CompletableFuture.runAsync(() -> {
            try {
                RealtyLogicImpl.SetPriceResult result = logic.setPrice(
                        regionId, region.world().getUID(), price);
                switch (result) {
                    case RealtyLogicImpl.SetPriceResult.Success ignored ->
                            sender.sendMessage(messages.messageFor("set-price.success",
                                    Placeholder.unparsed("price", String.valueOf(price)),
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyLogicImpl.SetPriceResult.NoFreeholdContract ignored ->
                            sender.sendMessage(messages.messageFor("set-price.no-freehold-contract",
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyLogicImpl.SetPriceResult.AuctionExists ignored ->
                            sender.sendMessage(messages.messageFor("set-price.auction-exists",
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyLogicImpl.SetPriceResult.OfferPaymentInProgress ignored ->
                            sender.sendMessage(messages.messageFor("set-price.offer-payment-in-progress",
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyLogicImpl.SetPriceResult.BidPaymentInProgress ignored ->
                            sender.sendMessage(messages.messageFor("set-price.bid-payment-in-progress",
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyLogicImpl.SetPriceResult.UpdateFailed ignored ->
                            sender.sendMessage(messages.messageFor("set-price.update-failed",
                                    Placeholder.unparsed("region", regionId)));
                }
            } catch (Exception ex) {
                sender.sendMessage(messages.messageFor("set-price.error",
                        Placeholder.unparsed("error", ex.getMessage())));
            }
        }, executorState.dbExec());
    }

    private void executeSetDuration(@NotNull CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.sender().getSender() instanceof Player sender)) {
            return;
        }
        Duration duration = ctx.get("duration");
        WorldGuardRegion region = ctx.get("region");
        String regionId = region.region().getId();
        CompletableFuture.runAsync(() -> {
            try {
                RealtyLogicImpl.SetDurationResult result = logic.setDuration(
                        regionId, region.world().getUID(), duration.toSeconds());
                switch (result) {
                    case RealtyLogicImpl.SetDurationResult.Success ignored ->
                            sender.sendMessage(messages.messageFor("set-duration.success",
                                    Placeholder.unparsed("duration", duration.toString()),
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyLogicImpl.SetDurationResult.NoLeaseContract ignored ->
                            sender.sendMessage(messages.messageFor("set-duration.no-lease-contract",
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyLogicImpl.SetDurationResult.UpdateFailed ignored ->
                            sender.sendMessage(messages.messageFor("set-duration.update-failed",
                                    Placeholder.unparsed("region", regionId)));
                }
            } catch (Exception ex) {
                sender.sendMessage(messages.messageFor("set-duration.error",
                        Placeholder.unparsed("error", ex.getMessage())));
            }
        }, executorState.dbExec());
    }

    private void executeSetLandlord(@NotNull CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.sender().getSender() instanceof Player sender)) {
            return;
        }
        UUID landlordId = ctx.get("landlord");
        WorldGuardRegion region = ctx.get("region");
        String regionId = region.region().getId();
        CompletableFuture.runAsync(() -> {
            try {
                RealtyLogicImpl.SetLandlordResult result = logic.setLandlord(
                        regionId, region.world().getUID(), landlordId);
                switch (result) {
                    case RealtyLogicImpl.SetLandlordResult.Success ignored ->
                            sender.sendMessage(messages.messageFor("set-landlord.success",
                                    Placeholder.unparsed("landlord", resolveName(landlordId)),
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyLogicImpl.SetLandlordResult.NoLeaseContract ignored ->
                            sender.sendMessage(messages.messageFor("set-landlord.no-lease-contract",
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyLogicImpl.SetLandlordResult.UpdateFailed ignored ->
                            sender.sendMessage(messages.messageFor("set-landlord.update-failed",
                                    Placeholder.unparsed("region", regionId)));
                }
            } catch (Exception ex) {
                sender.sendMessage(messages.messageFor("set-landlord.error",
                        Placeholder.unparsed("error", ex.getMessage())));
            }
        }, executorState.dbExec());
    }

    private void executeSetTitleHolder(@NotNull CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.sender().getSender() instanceof Player sender)) {
            return;
        }
        UUID titleHolderId = ctx.get("titleholder");
        WorldGuardRegion region = ctx.get("region");
        String regionId = region.region().getId();
        CompletableFuture.runAsync(() -> {
            try {
                RealtyLogicImpl.SetTitleHolderResult result = logic.setTitleHolder(
                        regionId, region.world().getUID(), titleHolderId);
                switch (result) {
                    case RealtyLogicImpl.SetTitleHolderResult.Success ignored -> {
                            Map<String, String> placeholders = logic.getRegionPlaceholders(regionId, region.world().getUID());
                            executorState.mainThreadExec().execute(
                                    () -> regionProfileService.applyFlags(region, RegionState.SOLD, placeholders));
                            sender.sendMessage(messages.messageFor("set-titleholder.success",
                                    Placeholder.unparsed("titleholder", resolveName(titleHolderId)),
                                    Placeholder.unparsed("region", regionId)));
                    }
                    case RealtyLogicImpl.SetTitleHolderResult.NoFreeholdContract ignored ->
                            sender.sendMessage(messages.messageFor("set-titleholder.no-freehold-contract",
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyLogicImpl.SetTitleHolderResult.UpdateFailed ignored ->
                            sender.sendMessage(messages.messageFor("set-titleholder.update-failed",
                                    Placeholder.unparsed("region", regionId)));
                }
            } catch (Exception ex) {
                sender.sendMessage(messages.messageFor("set-titleholder.error",
                        Placeholder.unparsed("error", ex.getMessage())));
            }
        }, executorState.dbExec());
    }

    private void executeSetTenant(@NotNull CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.sender().getSender() instanceof Player sender)) {
            return;
        }
        UUID tenantId = ctx.get("tenant");
        WorldGuardRegion region = ctx.get("region");
        String regionId = region.region().getId();
        CompletableFuture.runAsync(() -> {
            try {
                RealtyLogicImpl.SetTenantResult result = logic.setTenant(
                        regionId, region.world().getUID(), tenantId);
                switch (result) {
                    case RealtyLogicImpl.SetTenantResult.Success ignored -> {
                            Map<String, String> placeholders = logic.getRegionPlaceholders(regionId, region.world().getUID());
                            executorState.mainThreadExec().execute(
                                    () -> regionProfileService.applyFlags(region, RegionState.RENTED, placeholders));
                            sender.sendMessage(messages.messageFor("set-tenant.success",
                                    Placeholder.unparsed("tenant", resolveName(tenantId)),
                                    Placeholder.unparsed("region", regionId)));
                    }
                    case RealtyLogicImpl.SetTenantResult.NoLeaseContract ignored ->
                            sender.sendMessage(messages.messageFor("set-tenant.no-lease-contract",
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyLogicImpl.SetTenantResult.UpdateFailed ignored ->
                            sender.sendMessage(messages.messageFor("set-tenant.update-failed",
                                    Placeholder.unparsed("region", regionId)));
                }
            } catch (Exception ex) {
                sender.sendMessage(messages.messageFor("set-tenant.error",
                        Placeholder.unparsed("error", ex.getMessage())));
            }
        }, executorState.dbExec());
    }

    private static @NotNull String resolveName(@NotNull UUID uuid) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        String name = player.getName();
        return name != null ? name : uuid.toString();
    }

}
