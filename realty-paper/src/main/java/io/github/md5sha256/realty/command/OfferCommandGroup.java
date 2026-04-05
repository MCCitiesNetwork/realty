package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.api.CurrencyFormatter;
import io.github.md5sha256.realty.api.NotificationService;
import io.github.md5sha256.realty.api.RealtyBackend;
import io.github.md5sha256.realty.api.RealtyPaperApi;
import io.github.md5sha256.realty.command.util.ParseBounds;
import io.github.md5sha256.realty.api.WorldGuardRegion;
import io.github.md5sha256.realty.command.util.WorldGuardRegionResolver;
import io.github.md5sha256.realty.database.entity.InboundOfferView;
import io.github.md5sha256.realty.database.entity.OutboundOfferView;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.localisation.MessageKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.paper.util.sender.Source;
import org.incendo.cloud.parser.standard.BooleanParser;
import org.incendo.cloud.parser.standard.DoubleParser;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.suggestion.Suggestion;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.jetbrains.annotations.NotNull;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Groups all offer-related subcommands under {@code /realty offer}.
 *
 * <ul>
 *   <li>{@code /realty offer send <price> <region>}</li>
 *   <li>{@code /realty offer inbox}</li>
 *   <li>{@code /realty offer outbox}</li>
 *   <li>{@code /realty offer accept <player> <region>}</li>
 *   <li>{@code /realty offer pay <amount> <region>}</li>
 *   <li>{@code /realty offer withdraw <region>}</li>
 * </ul>
 */
public record OfferCommandGroup(
        @NotNull RealtyPaperApi api,
        @NotNull NotificationService notificationService,
        @NotNull MessageContainer messages
) implements CustomCommandBean {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Override
    public @NotNull List<Command<? extends Source>> commands(@NotNull Command.Builder<Source> builder) {
        var base = builder.literal("offer");
        return List.of(
                base.literal("send")
                        .permission("realty.command.offer.send")
                        .required("price", DoubleParser.doubleParser(ParseBounds.MIN_STRICTLY_POSITIVE,
                                Double.MAX_VALUE))
                        .optional("region", WorldGuardRegionResolver.worldGuardRegionResolver())
                        .handler(this::executeSend)
                        .build(),
                base.literal("inbox")
                        .permission("realty.command.offer.inbox")
                        .handler(this::executeInbox)
                        .build(),
                base.literal("outbox")
                        .permission("realty.command.offer.outbox")
                        .handler(this::executeOutbox)
                        .build(),
                base.literal("accept")
                        .permission("realty.command.offer.accept")
                        .required("player", StringParser.stringParser(), playerSuggestions())
                        .optional("region", WorldGuardRegionResolver.worldGuardRegionResolver())
                        .handler(this::executeAccept)
                        .build(),
                base.literal("pay")
                        .permission("realty.command.offer.pay")
                        .required("amount", DoubleParser.doubleParser(0, Double.MAX_VALUE))
                        .optional("region", WorldGuardRegionResolver.worldGuardRegionResolver())
                        .handler(this::executePay)
                        .build(),
                base.literal("withdraw")
                        .permission("realty.command.offer.withdraw")
                        .optional("region", WorldGuardRegionResolver.worldGuardRegionResolver())
                        .handler(this::executeWithdraw)
                        .build(),
                base.literal("reject")
                        .permission("realty.command.offer.reject")
                        .required("player", StringParser.stringParser(), playerSuggestions())
                        .optional("region", WorldGuardRegionResolver.worldGuardRegionResolver())
                        .handler(this::executeReject)
                        .build(),
                base.literal("rejectall")
                        .permission("realty.command.offer.reject")
                        .optional("region", WorldGuardRegionResolver.worldGuardRegionResolver())
                        .handler(this::executeRejectAll)
                        .build(),
                base.literal("toggle")
                        .permission("realty.command.offer.toggle")
                        .required("enabled", BooleanParser.booleanParser())
                        .optional("region", WorldGuardRegionResolver.worldGuardRegionResolver())
                        .handler(this::executeToggle)
                        .build()
        );
    }

    private static @NotNull SuggestionProvider<Source> playerSuggestions() {
        return (ctx, input) -> CompletableFuture.completedFuture(
                Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .map(Suggestion::suggestion)
                        .toList()
        );
    }

    // ── /realty offer send <price> <region> ──

    private void executeSend(@NotNull CommandContext<Source> ctx) {
        if (!(ctx.sender().source() instanceof Player sender)) {
            ctx.sender().source().sendMessage(messages.messageFor(MessageKeys.COMMON_PLAYERS_ONLY));
            return;
        }
        double price = ctx.get("price");
        WorldGuardRegion region = ctx.<WorldGuardRegion>optional("region")
                .orElseGet(() -> WorldGuardRegionResolver.resolveAtLocation(sender.getLocation()));
        if (region == null) {
            sender.sendMessage(messages.messageFor(MessageKeys.ERROR_NO_REGION));
            return;
        }
        String regionId = region.region().getId();
        api.placeOffer(regionId, region.world().getUID(), sender.getUniqueId(), price)
                .thenAccept(result -> {
                    switch (result) {
                        case RealtyBackend.OfferResult.Success success -> {
                            sender.sendMessage(messages.messageFor(MessageKeys.OFFER_SUCCESS,
                                    Placeholder.unparsed("price", CurrencyFormatter.format(price)),
                                    Placeholder.unparsed("region", regionId)));
                            if (success.titleHolderId() != null) {
                                notificationService.queueNotification(success.titleHolderId(),
                                        messages.messageFor(MessageKeys.NOTIFICATION_OFFER_PLACED,
                                                Placeholder.unparsed("player", sender.getName()),
                                                Placeholder.unparsed("price", CurrencyFormatter.format(price)),
                                                Placeholder.unparsed("region", regionId)));
                            }
                        }
                        case RealtyBackend.OfferResult.NoFreeholdContract ignored ->
                                sender.sendMessage(messages.messageFor(MessageKeys.OFFER_NO_FREEHOLD_CONTRACT,
                                        Placeholder.unparsed("region", regionId)));
                        case RealtyBackend.OfferResult.NotAcceptingOffers ignored ->
                                sender.sendMessage(messages.messageFor(MessageKeys.OFFER_NOT_ACCEPTING,
                                        Placeholder.unparsed("region", regionId)));
                        case RealtyBackend.OfferResult.IsOwner ignored ->
                                sender.sendMessage(messages.messageFor(MessageKeys.OFFER_IS_OWNER));
                        case RealtyBackend.OfferResult.AlreadyHasOffer ignored ->
                                sender.sendMessage(messages.messageFor(MessageKeys.OFFER_ALREADY_HAS_OFFER,
                                        Placeholder.unparsed("region", regionId)));
                        case RealtyBackend.OfferResult.AuctionExists ignored ->
                                sender.sendMessage(messages.messageFor(MessageKeys.OFFER_AUCTION_EXISTS,
                                        Placeholder.unparsed("region", regionId)));
                        case RealtyBackend.OfferResult.InsertFailed ignored ->
                                sender.sendMessage(messages.messageFor(MessageKeys.OFFER_INSERT_FAILED,
                                        Placeholder.unparsed("region", regionId)));
                    }
                }).exceptionally(ex -> {
                    sender.sendMessage(messages.messageFor(MessageKeys.OFFER_ERROR,
                            Placeholder.unparsed("error", ex.getMessage())));
                    return null;
                });
    }

    // ── /realty offer outbox ──

    private void executeOutbox(@NotNull CommandContext<Source> ctx) {
        if (!(ctx.sender().source() instanceof Player sender)) {
            ctx.sender().source().sendMessage(messages.messageFor(MessageKeys.COMMON_PLAYERS_ONLY));
            return;
        }
        api.listOutboundOffers(sender.getUniqueId()).thenAccept(offers -> {
            try {
                if (offers.isEmpty()) {
                    sender.sendMessage(messages.messageFor(MessageKeys.OFFERS_LIST_NO_OFFERS));
                    return;
                }

                Component output = messages.messageFor(MessageKeys.OFFERS_LIST_HEADER);

                for (OutboundOfferView offer : offers) {
                    String status;
                    if (offer.accepted()) {
                        double remaining = offer.offerPrice() - offer.currentPayment();
                        status = "Accepted — Paid " + String.format("%.2f", offer.currentPayment())
                                + " / " + String.format("%.2f", offer.offerPrice())
                                + " (remaining: " + String.format("%.2f", remaining) + ")";
                    } else {
                        status = "Pending";
                    }

                    output = output.appendNewline().append(messages.messageFor(MessageKeys.OFFERS_LIST_ENTRY,
                            Placeholder.unparsed("region", offer.worldGuardRegionId()),
                            Placeholder.unparsed("price", String.format("%.2f", offer.offerPrice())),
                            Placeholder.unparsed("date", offer.offerTime().format(DATE_FORMAT)),
                            Placeholder.unparsed("status", status)));
                }

                sender.sendMessage(output);
            } catch (Exception ex) {
                sender.sendMessage(messages.messageFor(MessageKeys.OFFERS_LIST_ERROR,
                        Placeholder.unparsed("error", ex.getMessage())));
            }
        });
    }

    // ── /realty offer inbox ──

    private void executeInbox(@NotNull CommandContext<Source> ctx) {
        if (!(ctx.sender().source() instanceof Player sender)) {
            ctx.sender().source().sendMessage(messages.messageFor(MessageKeys.COMMON_PLAYERS_ONLY));
            return;
        }
        api.listInboundOffers(sender.getUniqueId()).thenAccept(offers -> {
            try {
                if (offers.isEmpty()) {
                    sender.sendMessage(messages.messageFor(MessageKeys.OFFERS_INBOUND_NO_OFFERS));
                    return;
                }

                Component output = messages.messageFor(MessageKeys.OFFERS_INBOUND_HEADER);

                for (InboundOfferView offer : offers) {
                    OfflinePlayer offerer = Bukkit.getOfflinePlayer(offer.offererId());
                    String offererName = offerer.getName() != null ? offerer.getName() : offer.offererId().toString();

                    String status;
                    if (offer.accepted()) {
                        double remaining = offer.offerPrice() - offer.currentPayment();
                        status = "Accepted — Paid " + String.format("%.2f", offer.currentPayment())
                                + " / " + String.format("%.2f", offer.offerPrice())
                                + " (remaining: " + String.format("%.2f", remaining) + ")";
                    } else {
                        status = "Pending";
                    }

                    output = output.appendNewline().append(messages.messageFor(MessageKeys.OFFERS_INBOUND_ENTRY,
                            Placeholder.unparsed("region", offer.worldGuardRegionId()),
                            Placeholder.unparsed("player", offererName),
                            Placeholder.unparsed("price", String.format("%.2f", offer.offerPrice())),
                            Placeholder.unparsed("date", offer.offerTime().format(DATE_FORMAT)),
                            Placeholder.unparsed("status", status)));
                }

                sender.sendMessage(output);
            } catch (Exception ex) {
                sender.sendMessage(messages.messageFor(MessageKeys.OFFERS_INBOUND_ERROR,
                        Placeholder.unparsed("error", ex.getMessage())));
            }
        });
    }

    // ── /realty offer accept <player> <region> ──

    private void executeAccept(@NotNull CommandContext<Source> ctx) {
        if (!(ctx.sender().source() instanceof Player sender)) {
            ctx.sender().source().sendMessage(messages.messageFor(MessageKeys.COMMON_PLAYERS_ONLY));
            return;
        }
        String playerName = ctx.get("player");
        WorldGuardRegion region = ctx.<WorldGuardRegion>optional("region")
                .orElseGet(() -> WorldGuardRegionResolver.resolveAtLocation(sender.getLocation()));
        if (region == null) {
            sender.sendMessage(messages.messageFor(MessageKeys.ERROR_NO_REGION));
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage(messages.messageFor(MessageKeys.COMMON_PLAYER_NOT_FOUND,
                    Placeholder.unparsed("player", playerName)));
            return;
        }
        String regionId = region.region().getId();
        api.acceptOffer(regionId, region.world().getUID(), sender.getUniqueId(), target.getUniqueId())
                .thenAccept(result -> {
                    switch (result) {
                        case RealtyBackend.AcceptOfferResult.Success ignored -> {
                            sender.sendMessage(messages.messageFor(MessageKeys.ACCEPT_OFFER_SUCCESS,
                                    Placeholder.unparsed("player", playerName),
                                    Placeholder.unparsed("region", regionId)));
                            notificationService.queueNotification(target.getUniqueId(),
                                    messages.messageFor(MessageKeys.NOTIFICATION_OFFER_ACCEPTED,
                                            Placeholder.unparsed("region", regionId)));
                        }
                        case RealtyBackend.AcceptOfferResult.NotSanctioned ignored ->
                                sender.sendMessage(messages.messageFor(MessageKeys.ACCEPT_OFFER_NOT_SANCTIONED,
                                        Placeholder.unparsed("region", regionId)));
                        case RealtyBackend.AcceptOfferResult.NoOffer ignored ->
                                sender.sendMessage(messages.messageFor(MessageKeys.ACCEPT_OFFER_NO_OFFER,
                                        Placeholder.unparsed("player", playerName),
                                        Placeholder.unparsed("region", regionId)));
                        case RealtyBackend.AcceptOfferResult.AuctionExists ignored ->
                                sender.sendMessage(messages.messageFor(MessageKeys.ACCEPT_OFFER_AUCTION_EXISTS,
                                        Placeholder.unparsed("region", regionId)));
                        case RealtyBackend.AcceptOfferResult.AlreadyAccepted ignored ->
                                sender.sendMessage(messages.messageFor(MessageKeys.ACCEPT_OFFER_ALREADY_ACCEPTED,
                                        Placeholder.unparsed("region", regionId)));
                        case RealtyBackend.AcceptOfferResult.InsertFailed ignored ->
                                sender.sendMessage(messages.messageFor(MessageKeys.ACCEPT_OFFER_INSERT_FAILED,
                                        Placeholder.unparsed("region", regionId)));
                    }
                }).exceptionally(ex -> {
                    sender.sendMessage(messages.messageFor(MessageKeys.ACCEPT_OFFER_ERROR,
                            Placeholder.unparsed("error", ex.getMessage())));
                    return null;
                });
    }

    // ── /realty offer pay <amount> <region> ──

    private void executePay(@NotNull CommandContext<Source> ctx) {
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
        api.payOffer(region, sender.getUniqueId(), amount).thenAccept(result -> {
            switch (result) {
                case RealtyPaperApi.PayOfferResult.Success success ->
                        sender.sendMessage(messages.messageFor(MessageKeys.PAY_OFFER_SUCCESS,
                                Placeholder.unparsed("amount", CurrencyFormatter.format(success.amount())),
                                Placeholder.unparsed("region", success.regionId()),
                                Placeholder.unparsed("total", CurrencyFormatter.format(success.newTotal())),
                                Placeholder.unparsed("remaining", CurrencyFormatter.format(success.remaining()))));
                case RealtyPaperApi.PayOfferResult.FullyPaid fullyPaid -> {
                    sender.sendMessage(messages.messageFor(MessageKeys.PAY_OFFER_TRANSFER_SUCCESS,
                            Placeholder.unparsed("region", fullyPaid.regionId())));
                    if (fullyPaid.previousTitleHolderId() != null) {
                        notificationService.queueNotification(fullyPaid.previousTitleHolderId(),
                                messages.messageFor(MessageKeys.NOTIFICATION_OWNERSHIP_TRANSFERRED,
                                        Placeholder.unparsed("player", sender.getName()),
                                        Placeholder.unparsed("region", fullyPaid.regionId())));
                    }
                }
                case RealtyPaperApi.PayOfferResult.NoPaymentRecord noPayment ->
                        sender.sendMessage(messages.messageFor(MessageKeys.PAY_OFFER_NO_PAYMENT_RECORD,
                                Placeholder.unparsed("region", noPayment.regionId())));
                case RealtyPaperApi.PayOfferResult.ExceedsAmountOwed exceeds ->
                        sender.sendMessage(messages.messageFor(MessageKeys.PAY_OFFER_EXCEEDS_OWED,
                                Placeholder.unparsed("amount", CurrencyFormatter.format(exceeds.amount())),
                                Placeholder.unparsed("owed", CurrencyFormatter.format(exceeds.amountOwed())),
                                Placeholder.unparsed("region", exceeds.regionId())));
                case RealtyPaperApi.PayOfferResult.InsufficientFunds insufficient ->
                        sender.sendMessage(messages.messageFor(MessageKeys.PAY_OFFER_INSUFFICIENT_FUNDS,
                                Placeholder.unparsed("balance", CurrencyFormatter.format(insufficient.balance()))));
                case RealtyPaperApi.PayOfferResult.PaymentFailed failed ->
                        sender.sendMessage(messages.messageFor(MessageKeys.PAY_OFFER_PAYMENT_FAILED,
                                Placeholder.unparsed("error", failed.error())));
                case RealtyPaperApi.PayOfferResult.TransferFailed ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.PAY_OFFER_TRANSFER_FAILED));
                case RealtyPaperApi.PayOfferResult.Error error ->
                        sender.sendMessage(messages.messageFor(MessageKeys.PAY_OFFER_ERROR,
                                Placeholder.unparsed("error", error.message())));
            }
        });
    }

    // ── /realty offer withdraw [region] ──

    private void executeWithdraw(@NotNull CommandContext<Source> ctx) {
        if (!(ctx.sender().source() instanceof Player sender)) {
            ctx.sender().source().sendMessage(messages.messageFor(MessageKeys.COMMON_PLAYERS_ONLY));
            return;
        }
        WorldGuardRegion region = ctx.<WorldGuardRegion>optional("region")
                .orElseGet(() -> WorldGuardRegionResolver.resolveAtLocation(sender.getLocation()));
        if (region == null) {
            sender.sendMessage(messages.messageFor(MessageKeys.ERROR_NO_REGION));
            return;
        }
        String regionId = region.region().getId();
        api.withdrawOffer(regionId, region.world().getUID(), sender.getUniqueId())
                .thenAccept(result -> {
                    switch (result) {
                        case RealtyBackend.WithdrawOfferResult.Success(var titleHolderId) -> {
                            sender.sendMessage(messages.messageFor(MessageKeys.WITHDRAW_OFFER_SUCCESS,
                                    Placeholder.unparsed("region", regionId)));
                            if (titleHolderId != null) {
                                notificationService.queueNotification(titleHolderId,
                                        messages.messageFor(MessageKeys.NOTIFICATION_OFFER_WITHDRAWN,
                                                Placeholder.unparsed("player", sender.getName()),
                                                Placeholder.unparsed("region", regionId)));
                            }
                        }
                        case RealtyBackend.WithdrawOfferResult.NoOffer() ->
                                sender.sendMessage(messages.messageFor(MessageKeys.WITHDRAW_OFFER_NO_OFFER,
                                        Placeholder.unparsed("region", regionId)));
                        case RealtyBackend.WithdrawOfferResult.OfferAccepted() ->
                                sender.sendMessage(messages.messageFor(MessageKeys.WITHDRAW_OFFER_ACCEPTED,
                                        Placeholder.unparsed("region", regionId)));
                    }
                }).exceptionally(ex -> {
                    sender.sendMessage(messages.messageFor(MessageKeys.WITHDRAW_OFFER_ERROR,
                            Placeholder.unparsed("error", ex.getMessage())));
                    return null;
                });
    }

    // ── /realty offer reject <player> [region] ──

    private void executeReject(@NotNull CommandContext<Source> ctx) {
        if (!(ctx.sender().source() instanceof Player sender)) {
            ctx.sender().source().sendMessage(messages.messageFor(MessageKeys.COMMON_PLAYERS_ONLY));
            return;
        }
        String playerName = ctx.get("player");
        WorldGuardRegion region = ctx.<WorldGuardRegion>optional("region")
                .orElseGet(() -> WorldGuardRegionResolver.resolveAtLocation(sender.getLocation()));
        if (region == null) {
            sender.sendMessage(messages.messageFor(MessageKeys.ERROR_NO_REGION));
            return;
        }
        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage(messages.messageFor(MessageKeys.COMMON_PLAYER_NOT_FOUND,
                    Placeholder.unparsed("player", playerName)));
            return;
        }
        String regionId = region.region().getId();
        api.rejectOffer(regionId, region.world().getUID(), sender.getUniqueId(), target.getUniqueId())
                .thenAccept(result -> {
                    switch (result) {
                        case RealtyBackend.RejectOfferResult.Success ignored -> {
                            sender.sendMessage(messages.messageFor(MessageKeys.REJECT_OFFER_SUCCESS,
                                    Placeholder.unparsed("player", playerName),
                                    Placeholder.unparsed("region", regionId)));
                            notificationService.queueNotification(target.getUniqueId(),
                                    messages.messageFor(MessageKeys.NOTIFICATION_OFFER_REJECTED,
                                            Placeholder.unparsed("region", regionId)));
                        }
                        case RealtyBackend.RejectOfferResult.NotSanctioned ignored ->
                                sender.sendMessage(messages.messageFor(MessageKeys.REJECT_OFFER_NOT_SANCTIONED,
                                        Placeholder.unparsed("region", regionId)));
                        case RealtyBackend.RejectOfferResult.NoOffer ignored ->
                                sender.sendMessage(messages.messageFor(MessageKeys.REJECT_OFFER_NO_OFFER,
                                        Placeholder.unparsed("player", playerName),
                                        Placeholder.unparsed("region", regionId)));
                        case RealtyBackend.RejectOfferResult.OfferAccepted ignored ->
                                sender.sendMessage(messages.messageFor(MessageKeys.REJECT_OFFER_ACCEPTED,
                                        Placeholder.unparsed("region", regionId)));
                    }
                }).exceptionally(ex -> {
                    sender.sendMessage(messages.messageFor(MessageKeys.REJECT_OFFER_ERROR,
                            Placeholder.unparsed("error", ex.getMessage())));
                    return null;
                });
    }

    // ── /realty offer rejectall [region] ──

    private void executeRejectAll(@NotNull CommandContext<Source> ctx) {
        if (!(ctx.sender().source() instanceof Player sender)) {
            ctx.sender().source().sendMessage(messages.messageFor(MessageKeys.COMMON_PLAYERS_ONLY));
            return;
        }
        WorldGuardRegion region = ctx.<WorldGuardRegion>optional("region")
                .orElseGet(() -> WorldGuardRegionResolver.resolveAtLocation(sender.getLocation()));
        if (region == null) {
            sender.sendMessage(messages.messageFor(MessageKeys.ERROR_NO_REGION));
            return;
        }
        String regionId = region.region().getId();
        api.rejectAllOffers(regionId, region.world().getUID(), sender.getUniqueId())
                .thenAccept(result -> {
                    switch (result) {
                        case RealtyBackend.RejectAllOffersResult.Success success -> {
                            sender.sendMessage(messages.messageFor(MessageKeys.REJECT_OFFER_ALL_SUCCESS,
                                    Placeholder.unparsed("count", String.valueOf(success.offererIds().size())),
                                    Placeholder.unparsed("region", regionId)));
                            Component notification = messages.messageFor(MessageKeys.NOTIFICATION_OFFER_REJECTED,
                                    Placeholder.unparsed("region", regionId));
                            for (UUID offererId : success.offererIds()) {
                                notificationService.queueNotification(offererId, notification);
                            }
                        }
                        case RealtyBackend.RejectAllOffersResult.NotSanctioned ignored ->
                                sender.sendMessage(messages.messageFor(MessageKeys.REJECT_OFFER_NOT_SANCTIONED,
                                        Placeholder.unparsed("region", regionId)));
                        case RealtyBackend.RejectAllOffersResult.NoFreeholdContract ignored ->
                                sender.sendMessage(messages.messageFor(MessageKeys.REJECT_OFFER_NO_FREEHOLD_CONTRACT,
                                        Placeholder.unparsed("region", regionId)));
                        case RealtyBackend.RejectAllOffersResult.OfferAccepted ignored ->
                                sender.sendMessage(messages.messageFor(MessageKeys.REJECT_OFFER_ACCEPTED,
                                        Placeholder.unparsed("region", regionId)));
                    }
                }).exceptionally(ex -> {
                    sender.sendMessage(messages.messageFor(MessageKeys.REJECT_OFFER_ERROR,
                            Placeholder.unparsed("error", ex.getMessage())));
                    return null;
                });
    }

    // ── /realty offer toggle <yes/no> [region] ──

    private void executeToggle(@NotNull CommandContext<Source> ctx) {
        if (!(ctx.sender().source() instanceof Player sender)) {
            ctx.sender().source().sendMessage(messages.messageFor(MessageKeys.COMMON_PLAYERS_ONLY));
            return;
        }
        boolean accepting = ctx.get("enabled");
        WorldGuardRegion region = ctx.<WorldGuardRegion>optional("region")
                .orElseGet(() -> WorldGuardRegionResolver.resolveAtLocation(sender.getLocation()));
        if (region == null) {
            sender.sendMessage(messages.messageFor(MessageKeys.ERROR_NO_REGION));
            return;
        }
        String regionId = region.region().getId();
        boolean bypass = sender.hasPermission("realty.command.offer.toggle.bypass");
        api.toggleOffers(regionId, region.world().getUID(), sender.getUniqueId(), accepting, bypass)
                .thenAccept(result -> {
                    switch (result) {
                        case RealtyBackend.ToggleOffersResult.Success success ->
                                sender.sendMessage(messages.messageFor(MessageKeys.TOGGLE_OFFERS_SUCCESS,
                                        Placeholder.unparsed("region", regionId),
                                        Placeholder.unparsed("state", success.acceptingOffers() ? "yes" : "no")));
                        case RealtyBackend.ToggleOffersResult.NotSanctioned ignored ->
                                sender.sendMessage(messages.messageFor(MessageKeys.TOGGLE_OFFERS_NOT_SANCTIONED,
                                        Placeholder.unparsed("region", regionId)));
                        case RealtyBackend.ToggleOffersResult.NoFreeholdContract ignored ->
                                sender.sendMessage(messages.messageFor(MessageKeys.TOGGLE_OFFERS_NO_FREEHOLD_CONTRACT,
                                        Placeholder.unparsed("region", regionId)));
                        case RealtyBackend.ToggleOffersResult.UpdateFailed ignored ->
                                sender.sendMessage(messages.messageFor(MessageKeys.TOGGLE_OFFERS_UPDATE_FAILED,
                                        Placeholder.unparsed("region", regionId)));
                    }
                }).exceptionally(ex -> {
                    sender.sendMessage(messages.messageFor(MessageKeys.TOGGLE_OFFERS_ERROR,
                            Placeholder.unparsed("error", ex.getMessage())));
                    return null;
                });
    }

}
