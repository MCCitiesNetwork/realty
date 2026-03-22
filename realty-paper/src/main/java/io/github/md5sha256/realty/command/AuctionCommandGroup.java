package io.github.md5sha256.realty.command;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import io.github.md5sha256.realty.api.DurationFormatter;
import io.github.md5sha256.realty.api.NotificationService;
import io.github.md5sha256.realty.api.RegionProfileService;
import io.github.md5sha256.realty.api.RegionState;
import io.github.md5sha256.realty.api.SignTextApplicator;
import io.github.md5sha256.realty.command.util.DurationParser;
import io.github.md5sha256.realty.command.util.WorldGuardRegion;
import io.github.md5sha256.realty.command.util.WorldGuardRegionParser;
import io.github.md5sha256.realty.command.util.WorldGuardRegionResolver;
import io.github.md5sha256.realty.database.RealtyLogicImpl;
import io.github.md5sha256.realty.database.RealtyLogicImpl.CreateAuctionResult;
import io.github.md5sha256.realty.database.entity.FreeholdContractAuctionEntity;
import io.github.md5sha256.realty.database.entity.FreeholdContractBid;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.localisation.MessageKeys;
import io.github.md5sha256.realty.settings.Settings;
import io.github.md5sha256.realty.util.DateFormatter;
import io.github.md5sha256.realty.util.ExecutorState;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.DoubleParser;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Groups all auction-related subcommands under {@code /realty auction}.
 *
 * <ul>
 *   <li>{@code /realty auction <bidDuration> <paymentDuration> <minBid> <minBidStep> <region>} — create</li>
 *   <li>{@code /realty auction cancel [region]}</li>
 *   <li>{@code /realty auction bid <amount> <region>}</li>
 *   <li>{@code /realty auction paybid <amount> <region>}</li>
 * </ul>
 */
public record AuctionCommandGroup(
        @NotNull ExecutorState executorState,
        @NotNull RealtyLogicImpl logic,
        @NotNull Economy economy,
        @NotNull NotificationService notificationService,
        @NotNull RegionProfileService regionProfileService,
        @NotNull SignTextApplicator signTextApplicator,
        @NotNull AtomicReference<Settings> settings,
        @NotNull MessageContainer messages
) implements CustomCommandBean {

    @Override
    public @NotNull List<Command<CommandSourceStack>> commands(@NotNull Command.Builder<CommandSourceStack> builder) {
        var base = builder.literal("auction");
        return List.of(
                base.literal("info")
                        .permission("realty.command.auction.info")
                        .optional("region", WorldGuardRegionResolver.worldGuardRegionResolver())
                        .handler(this::executeInfo)
                        .build(),
                base.permission("realty.command.auction")
                        .required("bidDuration", DurationParser.duration())
                        .required("paymentDuration", DurationParser.duration())
                        .required("minBid", DoubleParser.doubleParser(0))
                        .required("minBidStep", DoubleParser.doubleParser(0))
                        .required("region", WorldGuardRegionParser.worldGuardRegion())
                        .handler(this::executeCreate)
                        .build(),
                base.literal("cancel")
                        .permission("realty.command.auction.cancel")
                        .optional("region", WorldGuardRegionResolver.worldGuardRegionResolver())
                        .handler(this::executeCancel)
                        .build(),
                base.literal("bid")
                        .permission("realty.command.auction.bid")
                        .required("bid", DoubleParser.doubleParser(0))
                        .required("region", WorldGuardRegionParser.worldGuardRegion())
                        .handler(this::executeBid)
                        .build(),
                base.literal("paybid")
                        .permission("realty.command.auction.paybid")
                        .required("amount", DoubleParser.doubleParser(0, Double.MAX_VALUE))
                        .required("region", WorldGuardRegionParser.worldGuardRegion())
                        .handler(this::executePayBid)
                        .build()
        );
    }

    // ── /realty auction info [region] ──

    private void executeInfo(@NotNull CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.sender().getSender();
        if (!(sender instanceof Player player)) {
            return;
        }
        WorldGuardRegion region = ctx.<WorldGuardRegion>optional("region")
                .orElseGet(() -> WorldGuardRegionResolver.resolveAtLocation(player.getLocation()));
        if (region == null) {
            sender.sendMessage(messages.messageFor(MessageKeys.ERROR_NO_REGION));
            return;
        }
        String regionId = region.region().getId();
        UUID worldId = region.world().getUID();

        CompletableFuture.runAsync(() -> {
            try {
                RealtyLogicImpl.RegionInfo regionInfo = logic.getRegionInfo(regionId, worldId);
                FreeholdContractAuctionEntity auction = regionInfo.auction();
                if (auction == null) {
                    sender.sendMessage(messages.messageFor(MessageKeys.AUCTION_INFO_NO_AUCTION,
                            Placeholder.unparsed("region", regionId)));
                    return;
                }
                TextComponent.Builder builder = Component.text();
                builder.append(messages.messageFor(MessageKeys.AUCTION_INFO_HEADER,
                        Placeholder.unparsed("region", regionId)));
                FreeholdContractBid highestBid = regionInfo.highestBid();
                String highestBidAmount = highestBid != null ? String.valueOf(highestBid.bidAmount()) : "N/A";
                String highestBidPlayer = highestBid != null ? resolveName(highestBid.bidderId()) : "N/A";
                LocalDateTime lastActivity = highestBid != null ? highestBid.bidTime() : auction.startDate();
                LocalDateTime biddingEndDate = lastActivity.plusSeconds(auction.biddingDurationSeconds());

                builder.appendNewline()
                        .append(messages.messageFor(MessageKeys.AUCTION_INFO_DETAILS,
                                Placeholder.unparsed("auctioneer", resolveName(auction.auctioneerId())),
                                Placeholder.unparsed("start_date", DateFormatter.format(settings.get(),auction.startDate())),
                                Placeholder.unparsed("duration",
                                        DurationFormatter.format(Duration.ofSeconds(auction.biddingDurationSeconds()))),
                                Placeholder.unparsed("bidding_end_date", DateFormatter.format(settings.get(), biddingEndDate)),
                                Placeholder.unparsed("deadline", DateFormatter.format(settings.get(),auction.paymentDeadline())),
                                Placeholder.unparsed("min_bid", String.valueOf(auction.minBid())),
                                Placeholder.unparsed("min_step", String.valueOf(auction.minStep())),
                                Placeholder.unparsed("highest_bid_amount", highestBidAmount),
                                Placeholder.unparsed("highest_bid_player", highestBidPlayer)));
                sender.sendMessage(builder.build());
            } catch (Exception ex) {
                sender.sendMessage(messages.messageFor(MessageKeys.AUCTION_INFO_ERROR,
                        Placeholder.unparsed("error", ex.getMessage())));
            }
        }, executorState.dbExec());
    }

    private static @NotNull String resolveName(@NotNull UUID uuid) {
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name != null ? name : uuid.toString();
    }


    // ── /realty auction <bidDuration> <paymentDuration> <minBid> <minBidStep> <region> ──

    private void executeCreate(@NotNull CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.sender().getSender();
        if (!(sender instanceof Player player)) {
            return;
        }
        Duration bidDuration = ctx.get("bidDuration");
        Duration paymentDuration = ctx.get("paymentDuration");
        double minBid = ctx.get("minBid");
        double minBidStep = ctx.get("minBidStep");
        WorldGuardRegion region = ctx.get("region");
        String regionId = region.region().getId();
        CompletableFuture.runAsync(() -> {
            try {
                CreateAuctionResult result = logic.createAuction(
                        regionId,
                        region.world().getUID(),
                        player.getUniqueId(),
                        bidDuration.toSeconds(),
                        paymentDuration.toSeconds(),
                        minBid,
                        minBidStep
                );
                switch (result) {
                    case CreateAuctionResult.Success ignored ->
                            sender.sendMessage(messages.messageFor(MessageKeys.AUCTION_SUCCESS,
                                    Placeholder.unparsed("region", regionId)));
                    case CreateAuctionResult.NotSanctioned ignored ->
                            sender.sendMessage(messages.messageFor(MessageKeys.AUCTION_NOT_SANCTIONED,
                                    Placeholder.unparsed("region", regionId)));
                    case CreateAuctionResult.NoFreeholdContract ignored ->
                            sender.sendMessage(messages.messageFor(MessageKeys.AUCTION_NO_FREEHOLD_CONTRACT,
                                    Placeholder.unparsed("region", regionId)));
                }
            } catch (Exception ex) {
                sender.sendMessage(messages.messageFor(MessageKeys.AUCTION_ERROR,
                        Placeholder.unparsed("error", ex.getMessage())));
            }
        }, executorState.dbExec());
    }

    // ── /realty auction cancel [region] ──

    private void executeCancel(@NotNull CommandContext<CommandSourceStack> ctx) {
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
        CompletableFuture.runAsync(() -> {
            try {
                RealtyLogicImpl.CancelAuctionResult result = logic.cancelAuction(regionId, region.world().getUID());
                if (result.deleted() == 0) {
                    sender.sendMessage(messages.messageFor(MessageKeys.CANCEL_AUCTION_NO_AUCTION));
                    return;
                }
                sender.sendMessage(messages.messageFor(MessageKeys.CANCEL_AUCTION_SUCCESS,
                        Placeholder.unparsed("region", regionId)));
                for (UUID bidderId : result.bidderIds()) {
                    notificationService.queueNotification(bidderId,
                            messages.messageFor(MessageKeys.NOTIFICATION_AUCTION_CANCELLED,
                                    Placeholder.unparsed("region", regionId)));
                }
            } catch (Exception ex) {
                sender.sendMessage(messages.messageFor(MessageKeys.CANCEL_AUCTION_ERROR,
                        Placeholder.unparsed("error", ex.getMessage())));
            }
        }, executorState.dbExec());
    }

    // ── /realty auction bid <amount> <region> ──

    private void executeBid(@NotNull CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.sender().getSender() instanceof Player sender)) {
            return;
        }
        double bidAmount = ctx.<Double>get("bid");
        WorldGuardRegion region = ctx.get("region");
        String regionId = region.region().getId();
        CompletableFuture.runAsync(() -> {
            try {
                RealtyLogicImpl.BidResult result = logic.performBid(
                        regionId, region.world().getUID(),
                        sender.getUniqueId(), bidAmount);
                switch (result) {
                    case RealtyLogicImpl.BidResult.Success success -> {
                            sender.sendMessage(messages.messageFor(MessageKeys.BID_SUCCESS,
                                    Placeholder.unparsed("amount", String.valueOf(bidAmount)),
                                    Placeholder.unparsed("region", regionId)));
                            if (success.previousBidderId() != null) {
                                notificationService.queueNotification(success.previousBidderId(),
                                        messages.messageFor(MessageKeys.NOTIFICATION_OUTBID,
                                                Placeholder.unparsed("region", regionId),
                                                Placeholder.unparsed("amount", String.valueOf(bidAmount))));
                            }
                    }
                    case RealtyLogicImpl.BidResult.NoAuction ignored ->
                            sender.sendMessage(messages.messageFor(MessageKeys.BID_NO_AUCTION));
                    case RealtyLogicImpl.BidResult.IsOwner ignored ->
                            sender.sendMessage(messages.messageFor(MessageKeys.BID_IS_OWNER));
                    case RealtyLogicImpl.BidResult.BidTooLowMinimum r ->
                            sender.sendMessage(messages.messageFor(MessageKeys.BID_TOO_LOW_MINIMUM,
                                    Placeholder.unparsed("amount", String.valueOf(r.minBid()))));
                    case RealtyLogicImpl.BidResult.BidTooLowCurrent r ->
                            sender.sendMessage(messages.messageFor(MessageKeys.BID_TOO_LOW_CURRENT,
                                    Placeholder.unparsed("amount", String.valueOf(r.currentHighest()))));
                    case RealtyLogicImpl.BidResult.AlreadyHighestBidder ignored ->
                            sender.sendMessage(messages.messageFor(MessageKeys.BID_ALREADY_HIGHEST));
                }
            } catch (Exception ex) {
                sender.sendMessage(messages.messageFor(MessageKeys.BID_ERROR,
                        Placeholder.unparsed("error", ex.getMessage())));
            }
        }, executorState.dbExec());
    }

    // ── /realty auction paybid <amount> <region> ──

    private void executePayBid(@NotNull CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.sender().getSender() instanceof Player sender)) {
            ctx.sender().getSender().sendMessage(messages.messageFor(MessageKeys.COMMON_PLAYERS_ONLY));
            return;
        }
        double amount = ctx.get("amount");
        WorldGuardRegion region = ctx.get("region");
        String regionId = region.region().getId();
        // Balance check on main thread
        double balance = economy.getBalance(sender);
        if (balance < amount) {
            sender.sendMessage(messages.messageFor(MessageKeys.PAY_BID_INSUFFICIENT_FUNDS,
                    Placeholder.unparsed("balance", String.valueOf(balance))));
            return;
        }
        EconomyResponse response = economy.withdrawPlayer(sender, amount);
        if (!response.transactionSuccess()) {
            sender.sendMessage(messages.messageFor(MessageKeys.PAY_BID_PAYMENT_FAILED,
                    Placeholder.unparsed("error", response.errorMessage)));
            return;
        }
        // DB logic on async thread
        CompletableFuture.supplyAsync(() -> {
            try {
                RealtyLogicImpl.PayBidResult result = logic.payBid(
                        regionId, region.world().getUID(),
                        sender.getUniqueId(), amount);
                return switch (result) {
                    case RealtyLogicImpl.PayBidResult.Success success -> {
                        sender.sendMessage(messages.messageFor(MessageKeys.PAY_BID_SUCCESS,
                                Placeholder.unparsed("amount", String.valueOf(amount)),
                                Placeholder.unparsed("region", regionId),
                                Placeholder.unparsed("total", String.valueOf(success.newTotal())),
                                Placeholder.unparsed("remaining", String.valueOf(success.remaining()))));
                        yield result;
                    }
                    case RealtyLogicImpl.PayBidResult.FullyPaid fullyPaid -> {
                        sender.sendMessage(messages.messageFor(MessageKeys.PAY_BID_FULLY_PAID,
                                Placeholder.unparsed("amount", String.valueOf(amount)),
                                Placeholder.unparsed("region", regionId)));
                        yield result;
                    }
                    case RealtyLogicImpl.PayBidResult.NoPaymentRecord ignored -> {
                        sender.sendMessage(messages.messageFor(MessageKeys.PAY_BID_NO_PAYMENT_RECORD,
                                Placeholder.unparsed("region", regionId)));
                        yield result;
                    }
                    case RealtyLogicImpl.PayBidResult.PaymentExpired ignored -> {
                        sender.sendMessage(messages.messageFor(MessageKeys.PAY_BID_PAYMENT_EXPIRED,
                                Placeholder.unparsed("region", regionId)));
                        yield result;
                    }
                    case RealtyLogicImpl.PayBidResult.ExceedsAmountOwed exceeds -> {
                        sender.sendMessage(messages.messageFor(MessageKeys.PAY_BID_EXCEEDS_OWED,
                                Placeholder.unparsed("amount", String.valueOf(amount)),
                                Placeholder.unparsed("owed", String.valueOf(exceeds.amountOwed())),
                                Placeholder.unparsed("region", regionId)));
                        yield result;
                    }
                };
            } catch (Exception ex) {
                sender.sendMessage(messages.messageFor(MessageKeys.PAY_BID_ERROR,
                        Placeholder.unparsed("error", ex.getMessage())));
                return null;
            }
        }, executorState.dbExec()).thenAcceptAsync(result -> {
            switch (result) {
                case RealtyLogicImpl.PayBidResult.Success success -> {
                    UUID recipientId = success.titleHolderId() != null
                            ? success.titleHolderId() : success.authorityId();
                    OfflinePlayer recipient = Bukkit.getOfflinePlayer(recipientId);
                    economy.depositPlayer(recipient, amount);
                }
                case RealtyLogicImpl.PayBidResult.FullyPaid fullyPaid -> {
                    UUID recipientId = fullyPaid.titleHolderId() != null
                            ? fullyPaid.titleHolderId() : fullyPaid.authorityId();
                    OfflinePlayer recipient = Bukkit.getOfflinePlayer(recipientId);
                    economy.depositPlayer(recipient, amount);
                    RegionManager regionManager = WorldGuard.getInstance()
                            .getPlatform()
                            .getRegionContainer()
                            .get(BukkitAdapter.adapt(region.world()));
                    if (regionManager == null) {
                        sender.sendMessage(messages.messageFor(MessageKeys.PAY_BID_TRANSFER_FAILED));
                        return;
                    }
                    ProtectedRegion protectedRegion = region.region();
                    protectedRegion.getOwners().clear();
                    protectedRegion.getOwners().addPlayer(sender.getUniqueId());
                    protectedRegion.getMembers().clear();
                    Map<String, String> placeholders = logic.getRegionPlaceholders(regionId, region.world().getUID());
                    regionProfileService.applyFlags(region, RegionState.SOLD, placeholders);
                    signTextApplicator.updateLoadedSigns(region.world(), regionId, RegionState.SOLD, placeholders);
                    sender.sendMessage(messages.messageFor(MessageKeys.PAY_BID_TRANSFER_SUCCESS,
                            Placeholder.unparsed("region", regionId)));
                    if (fullyPaid.titleHolderId() != null) {
                        notificationService.queueNotification(fullyPaid.titleHolderId(),
                                messages.messageFor(MessageKeys.NOTIFICATION_OWNERSHIP_TRANSFERRED,
                                        Placeholder.unparsed("player", sender.getName()),
                                        Placeholder.unparsed("region", regionId)));
                    }
                }
                case RealtyLogicImpl.PayBidResult.NoPaymentRecord ignored ->
                        economy.depositPlayer(sender, amount);
                case RealtyLogicImpl.PayBidResult.PaymentExpired ignored ->
                        economy.depositPlayer(sender, amount);
                case RealtyLogicImpl.PayBidResult.ExceedsAmountOwed ignored ->
                        economy.depositPlayer(sender, amount);
                case null -> economy.depositPlayer(sender, amount);
            }
        }, executorState.mainThreadExec());
    }

}
