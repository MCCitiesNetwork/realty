package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.api.CurrencyFormatter;
import io.github.md5sha256.realty.api.DurationFormatter;
import io.github.md5sha256.realty.api.NotificationService;
import io.github.md5sha256.realty.api.RealtyBackend;
import io.github.md5sha256.realty.api.RealtyPaperApi;
import io.github.md5sha256.realty.command.util.DurationParser;
import io.github.md5sha256.realty.command.util.ParseBounds;
import io.github.md5sha256.realty.command.util.WorldGuardRegion;
import io.github.md5sha256.realty.command.util.WorldGuardRegionResolver;
import io.github.md5sha256.realty.database.entity.FreeholdContractAuctionEntity;
import io.github.md5sha256.realty.database.entity.FreeholdContractBid;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.localisation.MessageKeys;
import io.github.md5sha256.realty.settings.Settings;
import io.github.md5sha256.realty.util.DateFormatter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.paper.util.sender.Source;
import org.incendo.cloud.parser.standard.DoubleParser;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
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
        @NotNull RealtyPaperApi api,
        @NotNull NotificationService notificationService,
        @NotNull AtomicReference<Settings> settings,
        @NotNull MessageContainer messages
) implements CustomCommandBean {

    @Override
    public @NotNull List<Command<? extends Source>> commands(@NotNull Command.Builder<Source> builder) {
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
                        .required("minBid", DoubleParser.doubleParser(ParseBounds.MIN_STRICTLY_POSITIVE,
                                Double.MAX_VALUE))
                        .required("minBidStep", DoubleParser.doubleParser(ParseBounds.MIN_STRICTLY_POSITIVE,
                                Double.MAX_VALUE))
                        .optional("region", WorldGuardRegionResolver.worldGuardRegionResolver())
                        .handler(this::executeCreate)
                        .build(),
                base.literal("cancel")
                        .permission("realty.command.auction.cancel")
                        .optional("region", WorldGuardRegionResolver.worldGuardRegionResolver())
                        .handler(this::executeCancel)
                        .build(),
                base.literal("bid")
                        .permission("realty.command.auction.bid")
                        .required("bid", DoubleParser.doubleParser(ParseBounds.MIN_STRICTLY_POSITIVE,
                                Double.MAX_VALUE))
                        .optional("region", WorldGuardRegionResolver.worldGuardRegionResolver())
                        .handler(this::executeBid)
                        .build(),
                base.literal("paybid")
                        .permission("realty.command.auction.paybid")
                        .required("amount", DoubleParser.doubleParser(0, Double.MAX_VALUE))
                        .optional("region", WorldGuardRegionResolver.worldGuardRegionResolver())
                        .handler(this::executePayBid)
                        .build()
        );
    }

    // ── /realty auction info [region] ──

    private void executeInfo(@NotNull CommandContext<Source> ctx) {
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

        api.getRegionInfo(regionId, worldId).thenAccept(regionInfo -> {
            try {
                FreeholdContractAuctionEntity auction = regionInfo.auction();
                if (auction == null) {
                    sender.sendMessage(messages.messageFor(MessageKeys.AUCTION_INFO_NO_AUCTION,
                            Placeholder.unparsed("region", regionId)));
                    return;
                }
                TextComponent.Builder textBuilder = Component.text();
                textBuilder.append(messages.messageFor(MessageKeys.AUCTION_INFO_HEADER,
                        Placeholder.unparsed("region", regionId)));
                FreeholdContractBid highestBid = regionInfo.highestBid();
                String highestBidAmount = highestBid != null ? CurrencyFormatter.format(highestBid.bidAmount()) : "N/A";
                String highestBidPlayer = highestBid != null ? resolveName(highestBid.bidderId()) : "N/A";
                LocalDateTime lastActivity = highestBid != null ? highestBid.bidTime() : auction.startDate();
                LocalDateTime biddingEndDate = lastActivity.plusSeconds(auction.biddingDurationSeconds());

                textBuilder.appendNewline()
                        .append(messages.messageFor(MessageKeys.AUCTION_INFO_DETAILS,
                                Placeholder.unparsed("auctioneer", resolveName(auction.auctioneerId())),
                                Placeholder.unparsed("start_date", DateFormatter.format(settings.get(), auction.startDate())),
                                Placeholder.unparsed("duration",
                                        DurationFormatter.format(Duration.ofSeconds(auction.biddingDurationSeconds()))),
                                Placeholder.unparsed("bidding_end_date", DateFormatter.format(settings.get(), biddingEndDate)),
                                Placeholder.unparsed("deadline", DateFormatter.format(settings.get(), auction.paymentDeadline())),
                                Placeholder.unparsed("min_bid", CurrencyFormatter.format(auction.minBid())),
                                Placeholder.unparsed("min_step", CurrencyFormatter.format(auction.minStep())),
                                Placeholder.unparsed("highest_bid_amount", highestBidAmount),
                                Placeholder.unparsed("highest_bid_player", highestBidPlayer)));
                sender.sendMessage(textBuilder.build());
            } catch (Exception ex) {
                sender.sendMessage(messages.messageFor(MessageKeys.AUCTION_INFO_ERROR,
                        Placeholder.unparsed("error", ex.getMessage())));
            }
        });
    }

    private static @NotNull String resolveName(@NotNull UUID uuid) {
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name != null ? name : uuid.toString();
    }

    // ── /realty auction <bidDuration> <paymentDuration> <minBid> <minBidStep> <region> ──

    private void executeCreate(@NotNull CommandContext<Source> ctx) {
        CommandSender sender = ctx.sender().source();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.messageFor(MessageKeys.COMMON_PLAYERS_ONLY));
            return;
        }
        Duration bidDuration = ctx.get("bidDuration");
        Duration paymentDuration = ctx.get("paymentDuration");
        double minBid = ctx.get("minBid");
        double minBidStep = ctx.get("minBidStep");
        WorldGuardRegion region = ctx.<WorldGuardRegion>optional("region")
                .orElseGet(() -> WorldGuardRegionResolver.resolveAtLocation(player.getLocation()));
        if (region == null) {
            sender.sendMessage(messages.messageFor(MessageKeys.ERROR_NO_REGION));
            return;
        }
        String regionId = region.region().getId();
        api.createAuction(
                regionId,
                region.world().getUID(),
                player.getUniqueId(),
                bidDuration.toSeconds(),
                paymentDuration.toSeconds(),
                minBid,
                minBidStep
        ).thenAccept(result -> {
            switch (result) {
                case RealtyBackend.CreateAuctionResult.Success ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.AUCTION_SUCCESS,
                                Placeholder.unparsed("region", regionId)));
                case RealtyBackend.CreateAuctionResult.NotSanctioned ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.AUCTION_NOT_SANCTIONED,
                                Placeholder.unparsed("region", regionId)));
                case RealtyBackend.CreateAuctionResult.NoFreeholdContract ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.AUCTION_NO_FREEHOLD_CONTRACT,
                                Placeholder.unparsed("region", regionId)));
            }
        }).exceptionally(ex -> {
            sender.sendMessage(messages.messageFor(MessageKeys.AUCTION_ERROR,
                    Placeholder.unparsed("error", ex.getMessage())));
            return null;
        });
    }

    // ── /realty auction cancel [region] ──

    private void executeCancel(@NotNull CommandContext<Source> ctx) {
        CommandSender sender = ctx.sender().source();
        WorldGuardRegion region = ctx.<WorldGuardRegion>optional("region")
                .orElseGet(() -> sender instanceof Player player
                        ? WorldGuardRegionResolver.resolveAtLocation(player.getLocation()) : null);
        if (region == null) {
            sender.sendMessage(messages.messageFor(MessageKeys.ERROR_NO_REGION));
            return;
        }
        String regionId = region.region().getId();
        api.cancelAuction(regionId, region.world().getUID()).thenAccept(result -> {
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
        }).exceptionally(ex -> {
            sender.sendMessage(messages.messageFor(MessageKeys.CANCEL_AUCTION_ERROR,
                    Placeholder.unparsed("error", ex.getMessage())));
            return null;
        });
    }

    // ── /realty auction bid <amount> <region> ──

    private void executeBid(@NotNull CommandContext<Source> ctx) {
        if (!(ctx.sender().source() instanceof Player sender)) {
            ctx.sender().source().sendMessage(messages.messageFor(MessageKeys.COMMON_PLAYERS_ONLY));
            return;
        }
        double bidAmount = ctx.<Double>get("bid");
        WorldGuardRegion region = ctx.<WorldGuardRegion>optional("region")
                .orElseGet(() -> WorldGuardRegionResolver.resolveAtLocation(sender.getLocation()));
        if (region == null) {
            sender.sendMessage(messages.messageFor(MessageKeys.ERROR_NO_REGION));
            return;
        }
        String regionId = region.region().getId();
        api.performBid(regionId, region.world().getUID(), sender.getUniqueId(), bidAmount)
                .thenAccept(result -> {
                    switch (result) {
                        case RealtyBackend.BidResult.Success success -> {
                            sender.sendMessage(messages.messageFor(MessageKeys.BID_SUCCESS,
                                    Placeholder.unparsed("amount", CurrencyFormatter.format(bidAmount)),
                                    Placeholder.unparsed("region", regionId)));
                            if (success.previousBidderId() != null) {
                                notificationService.queueNotification(success.previousBidderId(),
                                        messages.messageFor(MessageKeys.NOTIFICATION_OUTBID,
                                                Placeholder.unparsed("region", regionId),
                                                Placeholder.unparsed("amount", CurrencyFormatter.format(bidAmount))));
                            }
                        }
                        case RealtyBackend.BidResult.NoAuction ignored ->
                                sender.sendMessage(messages.messageFor(MessageKeys.BID_NO_AUCTION));
                        case RealtyBackend.BidResult.IsOwner ignored ->
                                sender.sendMessage(messages.messageFor(MessageKeys.BID_IS_OWNER));
                        case RealtyBackend.BidResult.BidTooLowMinimum r ->
                                sender.sendMessage(messages.messageFor(MessageKeys.BID_TOO_LOW_MINIMUM,
                                        Placeholder.unparsed("amount", CurrencyFormatter.format(r.minBid()))));
                        case RealtyBackend.BidResult.BidTooLowCurrent r ->
                                sender.sendMessage(messages.messageFor(MessageKeys.BID_TOO_LOW_CURRENT,
                                        Placeholder.unparsed("amount", CurrencyFormatter.format(r.currentHighest()))));
                        case RealtyBackend.BidResult.AlreadyHighestBidder ignored ->
                                sender.sendMessage(messages.messageFor(MessageKeys.BID_ALREADY_HIGHEST));
                    }
                }).exceptionally(ex -> {
                    sender.sendMessage(messages.messageFor(MessageKeys.BID_ERROR,
                            Placeholder.unparsed("error", ex.getMessage())));
                    return null;
                });
    }

    // ── /realty auction paybid <amount> <region> ──

    private void executePayBid(@NotNull CommandContext<Source> ctx) {
        if (!(ctx.sender().source() instanceof Player sender)) {
            ctx.sender().source().sendMessage(messages.messageFor(MessageKeys.COMMON_PLAYERS_ONLY));
            return;
        }
        double amount = ctx.get("amount");
        WorldGuardRegion region = ctx.<WorldGuardRegion>optional("region")
                .orElseGet(() -> WorldGuardRegionResolver.resolveAtLocation(sender.getLocation()));
        if (region == null) {
            sender.sendMessage(messages.messageFor(MessageKeys.ERROR_NO_REGION));
            return;
        }
        String regionId = region.region().getId();
        api.payBid(region, sender.getUniqueId(), amount).thenAccept(result -> {
            switch (result) {
                case RealtyPaperApi.PayBidResult.Success success ->
                        sender.sendMessage(messages.messageFor(MessageKeys.PAY_BID_SUCCESS,
                                Placeholder.unparsed("amount", CurrencyFormatter.format(success.amount())),
                                Placeholder.unparsed("region", success.regionId()),
                                Placeholder.unparsed("total", CurrencyFormatter.format(success.newTotal())),
                                Placeholder.unparsed("remaining", CurrencyFormatter.format(success.remaining()))));
                case RealtyPaperApi.PayBidResult.FullyPaid fullyPaid -> {
                    sender.sendMessage(messages.messageFor(MessageKeys.PAY_BID_TRANSFER_SUCCESS,
                            Placeholder.unparsed("region", fullyPaid.regionId())));
                    if (fullyPaid.previousTitleHolderId() != null) {
                        notificationService.queueNotification(fullyPaid.previousTitleHolderId(),
                                messages.messageFor(MessageKeys.NOTIFICATION_OWNERSHIP_TRANSFERRED,
                                        Placeholder.unparsed("player", sender.getName()),
                                        Placeholder.unparsed("region", fullyPaid.regionId())));
                    }
                }
                case RealtyPaperApi.PayBidResult.NoPaymentRecord noPayment ->
                        sender.sendMessage(messages.messageFor(MessageKeys.PAY_BID_NO_PAYMENT_RECORD,
                                Placeholder.unparsed("region", noPayment.regionId())));
                case RealtyPaperApi.PayBidResult.PaymentExpired expired ->
                        sender.sendMessage(messages.messageFor(MessageKeys.PAY_BID_PAYMENT_EXPIRED,
                                Placeholder.unparsed("region", expired.regionId())));
                case RealtyPaperApi.PayBidResult.ExceedsAmountOwed exceeds ->
                        sender.sendMessage(messages.messageFor(MessageKeys.PAY_BID_EXCEEDS_OWED,
                                Placeholder.unparsed("amount", CurrencyFormatter.format(exceeds.amount())),
                                Placeholder.unparsed("owed", CurrencyFormatter.format(exceeds.amountOwed())),
                                Placeholder.unparsed("region", exceeds.regionId())));
                case RealtyPaperApi.PayBidResult.InsufficientFunds insufficient ->
                        sender.sendMessage(messages.messageFor(MessageKeys.PAY_BID_INSUFFICIENT_FUNDS,
                                Placeholder.unparsed("balance", CurrencyFormatter.format(insufficient.balance()))));
                case RealtyPaperApi.PayBidResult.PaymentFailed failed ->
                        sender.sendMessage(messages.messageFor(MessageKeys.PAY_BID_PAYMENT_FAILED,
                                Placeholder.unparsed("error", failed.error())));
                case RealtyPaperApi.PayBidResult.TransferFailed ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.PAY_BID_TRANSFER_FAILED));
                case RealtyPaperApi.PayBidResult.Error error ->
                        sender.sendMessage(messages.messageFor(MessageKeys.PAY_BID_ERROR,
                                Placeholder.unparsed("error", error.message())));
            }
        });
    }

}
