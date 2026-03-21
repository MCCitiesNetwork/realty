package io.github.md5sha256.realty.command;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import io.github.md5sha256.realty.api.NotificationService;
import io.github.md5sha256.realty.command.util.WorldGuardRegion;
import io.github.md5sha256.realty.command.util.WorldGuardRegionParser;
import io.github.md5sha256.realty.command.util.WorldGuardRegionResolver;
import io.github.md5sha256.realty.database.RealtyLogicImpl;
import io.github.md5sha256.realty.database.entity.InboundOfferView;
import io.github.md5sha256.realty.database.entity.OutboundOfferView;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.util.ExecutorState;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
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
        @NotNull ExecutorState executorState,
        @NotNull RealtyLogicImpl logic,
        @NotNull Economy economy,
        @NotNull NotificationService notificationService,
        @NotNull MessageContainer messages
) implements CustomCommandBean {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");


    @Override
    public @NotNull List<Command<CommandSourceStack>> commands(@NotNull CommandManager<CommandSourceStack> manager) {
        var base = manager.commandBuilder("realty").literal("offer");
        return List.of(
                base.literal("send")
                        .permission("realty.command.offer.send")
                        .required("price", DoubleParser.doubleParser(0, Double.MAX_VALUE))
                        .required("region", WorldGuardRegionParser.worldGuardRegion())
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
                        .required("region", WorldGuardRegionParser.worldGuardRegion())
                        .handler(this::executeAccept)
                        .build(),
                base.literal("pay")
                        .permission("realty.command.offer.pay")
                        .required("amount", DoubleParser.doubleParser(0, Double.MAX_VALUE))
                        .required("region", WorldGuardRegionParser.worldGuardRegion())
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
                        .build()
        );
    }

    private static @NotNull SuggestionProvider<CommandSourceStack> playerSuggestions() {
        return (ctx, input) -> CompletableFuture.completedFuture(
                Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .map(Suggestion::suggestion)
                        .toList()
        );
    }

    // ── /realty offer send <price> <region> ──

    private void executeSend(@NotNull CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.sender().getSender() instanceof Player sender)) {
            return;
        }
        double price = ctx.get("price");
        WorldGuardRegion region = ctx.get("region");
        String regionId = region.region().getId();
        CompletableFuture.runAsync(() -> {
            try {
                RealtyLogicImpl.OfferResult result = logic.placeOffer(
                        regionId, region.world().getUID(),
                        sender.getUniqueId(), price);
                switch (result) {
                    case RealtyLogicImpl.OfferResult.Success success -> {
                            sender.sendMessage(messages.messageFor("offer.success",
                                    Placeholder.unparsed("price", String.valueOf(price)),
                                    Placeholder.unparsed("region", regionId)));
                            notificationService.queueNotification(success.titleHolderId(),
                                    messages.messageFor("notification.offer-placed",
                                            Placeholder.unparsed("player", sender.getName()),
                                            Placeholder.unparsed("price", String.valueOf(price)),
                                            Placeholder.unparsed("region", regionId)));
                    }
                    case RealtyLogicImpl.OfferResult.NoSaleContract ignored ->
                            sender.sendMessage(messages.messageFor("offer.no-sale-contract",
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyLogicImpl.OfferResult.IsOwner ignored ->
                            sender.sendMessage(messages.messageFor("offer.is-owner"));
                    case RealtyLogicImpl.OfferResult.AlreadyHasOffer ignored ->
                            sender.sendMessage(messages.messageFor("offer.already-has-offer",
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyLogicImpl.OfferResult.AuctionExists ignored ->
                            sender.sendMessage(messages.messageFor("offer.auction-exists",
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyLogicImpl.OfferResult.InsertFailed ignored ->
                            sender.sendMessage(messages.messageFor("offer.insert-failed",
                                    Placeholder.unparsed("region", regionId)));
                }
            } catch (Exception ex) {
                sender.sendMessage(messages.messageFor("offer.error",
                        Placeholder.unparsed("error", ex.getMessage())));
            }
        }, executorState.dbExec());
    }

    // ── /realty offer outbox ──

    private void executeOutbox(@NotNull CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.sender().getSender() instanceof Player sender)) {
            ctx.sender().getSender().sendMessage(messages.messageFor("common.players-only"));
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                List<OutboundOfferView> offers = logic.listOutboundOffers(sender.getUniqueId());

                if (offers.isEmpty()) {
                    sender.sendMessage(messages.messageFor("offers-list.no-offers"));
                    return;
                }

                Component output = messages.messageFor("offers-list.header");

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

                    output = output.appendNewline().append(messages.messageFor("offers-list.entry",
                            Placeholder.unparsed("region", offer.worldGuardRegionId()),
                            Placeholder.unparsed("price", String.format("%.2f", offer.offerPrice())),
                            Placeholder.unparsed("date", offer.offerTime().format(DATE_FORMAT)),
                            Placeholder.unparsed("status", status)));
                }

                sender.sendMessage(output);
            } catch (Exception ex) {
                sender.sendMessage(messages.messageFor("offers-list.error",
                        Placeholder.unparsed("error", ex.getMessage())));
            }
        }, executorState.dbExec());
    }

    // ── /realty offer inbox ──

    private void executeInbox(@NotNull CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.sender().getSender() instanceof Player sender)) {
            ctx.sender().getSender().sendMessage(messages.messageFor("common.players-only"));
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                List<InboundOfferView> offers = logic.listInboundOffers(sender.getUniqueId());

                if (offers.isEmpty()) {
                    sender.sendMessage(messages.messageFor("offers-inbound.no-offers"));
                    return;
                }

                Component output = messages.messageFor("offers-inbound.header");

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

                    output = output.appendNewline().append(messages.messageFor("offers-inbound.entry",
                            Placeholder.unparsed("region", offer.worldGuardRegionId()),
                            Placeholder.unparsed("player", offererName),
                            Placeholder.unparsed("price", String.format("%.2f", offer.offerPrice())),
                            Placeholder.unparsed("date", offer.offerTime().format(DATE_FORMAT)),
                            Placeholder.unparsed("status", status)));
                }

                sender.sendMessage(output);
            } catch (Exception ex) {
                sender.sendMessage(messages.messageFor("offers-inbound.error",
                        Placeholder.unparsed("error", ex.getMessage())));
            }
        }, executorState.dbExec());
    }

    // ── /realty offer accept <player> <region> ──

    private void executeAccept(@NotNull CommandContext<CommandSourceStack> ctx) {
        String playerName = ctx.get("player");
        WorldGuardRegion region = ctx.get("region");
        CommandSender sender = ctx.sender().getSender();
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage(messages.messageFor("common.player-not-found",
                    Placeholder.unparsed("player", playerName)));
            return;
        }
        String regionId = region.region().getId();
        CompletableFuture.runAsync(() -> {
            try {
                RealtyLogicImpl.AcceptOfferResult result = logic.acceptOffer(
                        regionId, region.world().getUID(),
                        target.getUniqueId());
                switch (result) {
                    case RealtyLogicImpl.AcceptOfferResult.Success ignored -> {
                            sender.sendMessage(messages.messageFor("accept-offer.success",
                                    Placeholder.unparsed("player", playerName),
                                    Placeholder.unparsed("region", regionId)));
                            notificationService.queueNotification(target.getUniqueId(),
                                    messages.messageFor("notification.offer-accepted",
                                            Placeholder.unparsed("region", regionId)));
                    }
                    case RealtyLogicImpl.AcceptOfferResult.NoOffer ignored ->
                            sender.sendMessage(messages.messageFor("accept-offer.no-offer",
                                    Placeholder.unparsed("player", playerName),
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyLogicImpl.AcceptOfferResult.AuctionExists ignored ->
                            sender.sendMessage(messages.messageFor("accept-offer.auction-exists",
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyLogicImpl.AcceptOfferResult.AlreadyAccepted ignored ->
                            sender.sendMessage(messages.messageFor("accept-offer.already-accepted",
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyLogicImpl.AcceptOfferResult.InsertFailed ignored ->
                            sender.sendMessage(messages.messageFor("accept-offer.insert-failed",
                                    Placeholder.unparsed("region", regionId)));
                }
            } catch (Exception ex) {
                sender.sendMessage(messages.messageFor("accept-offer.error",
                        Placeholder.unparsed("error", ex.getMessage())));
            }
        }, executorState.dbExec());
    }

    // ── /realty offer pay <amount> <region> ──

    private void executePay(@NotNull CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.sender().getSender() instanceof Player sender)) {
            ctx.sender().getSender().sendMessage(messages.messageFor("common.players-only"));
            return;
        }
        double amount = ctx.get("amount");
        WorldGuardRegion region = ctx.get("region");
        String regionId = region.region().getId();
        // Balance check on main thread
        double balance = economy.getBalance(sender);
        if (balance < amount) {
            sender.sendMessage(messages.messageFor("pay-offer.insufficient-funds",
                    Placeholder.unparsed("balance", String.valueOf(balance))));
            return;
        }
        EconomyResponse response = economy.withdrawPlayer(sender, amount);
        if (!response.transactionSuccess()) {
            sender.sendMessage(messages.messageFor("pay-offer.payment-failed",
                    Placeholder.unparsed("error", response.errorMessage)));
            return;
        }
        // DB logic on async thread
        CompletableFuture.supplyAsync(() -> {
            try {
                RealtyLogicImpl.PayOfferResult result = logic.payOffer(
                        regionId, region.world().getUID(),
                        sender.getUniqueId(), amount);
                return switch (result) {
                    case RealtyLogicImpl.PayOfferResult.Success success -> {
                        sender.sendMessage(messages.messageFor("pay-offer.success",
                                Placeholder.unparsed("amount", String.valueOf(amount)),
                                Placeholder.unparsed("region", regionId),
                                Placeholder.unparsed("total", String.valueOf(success.newTotal())),
                                Placeholder.unparsed("remaining", String.valueOf(success.remaining()))));
                        yield null;
                    }
                    case RealtyLogicImpl.PayOfferResult.FullyPaid fullyPaid -> {
                        sender.sendMessage(messages.messageFor("pay-offer.fully-paid",
                                Placeholder.unparsed("amount", String.valueOf(amount)),
                                Placeholder.unparsed("region", regionId)));
                        yield fullyPaid;
                    }
                    case RealtyLogicImpl.PayOfferResult.NoPaymentRecord ignored -> {
                        sender.sendMessage(messages.messageFor("pay-offer.no-payment-record",
                                Placeholder.unparsed("region", regionId)));
                        yield null;
                    }
                    case RealtyLogicImpl.PayOfferResult.ExceedsAmountOwed exceeds -> {
                        sender.sendMessage(messages.messageFor("pay-offer.exceeds-owed",
                                Placeholder.unparsed("amount", String.valueOf(amount)),
                                Placeholder.unparsed("owed", String.valueOf(exceeds.amountOwed())),
                                Placeholder.unparsed("region", regionId)));
                        yield null;
                    }
                };
            } catch (Exception ex) {
                sender.sendMessage(messages.messageFor("pay-offer.error",
                        Placeholder.unparsed("error", ex.getMessage())));
                return null;
            }
        }, executorState.dbExec()).thenAcceptAsync(fullyPaid -> {
            if (fullyPaid == null) {
                economy.depositPlayer(sender, amount);
            } else {
                OfflinePlayer authority = Bukkit.getOfflinePlayer(fullyPaid.authorityId());
                economy.depositPlayer(authority, amount);
                RegionManager regionManager = WorldGuard.getInstance()
                        .getPlatform()
                        .getRegionContainer()
                        .get(BukkitAdapter.adapt(region.world()));
                if (regionManager == null) {
                    sender.sendMessage(messages.messageFor("pay-offer.transfer-failed"));
                    return;
                }
                ProtectedRegion protectedRegion = region.region();
                protectedRegion.getOwners().clear();
                protectedRegion.getOwners().addPlayer(sender.getUniqueId());
                protectedRegion.getMembers().clear();
                sender.sendMessage(messages.messageFor("pay-offer.transfer-success",
                        Placeholder.unparsed("region", regionId)));
                if (fullyPaid.titleHolderId() != null) {
                    notificationService.queueNotification(fullyPaid.titleHolderId(),
                            messages.messageFor("notification.ownership-transferred",
                                    Placeholder.unparsed("player", sender.getName()),
                                    Placeholder.unparsed("region", regionId)));
                }
            }
        }, executorState.mainThreadExec());
    }

    // ── /realty offer withdraw [region] ──

    private void executeWithdraw(@NotNull CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.sender().getSender() instanceof Player sender)) {
            return;
        }
        WorldGuardRegion region = ctx.<WorldGuardRegion>optional("region")
                .orElseGet(() -> WorldGuardRegionResolver.resolveAtLocation(sender.getLocation()));
        if (region == null) {
            sender.sendMessage(messages.messageFor("error.no-region"));
            return;
        }
        String regionId = region.region().getId();
        CompletableFuture.runAsync(() -> {
            try {
                RealtyLogicImpl.WithdrawOfferResult result = logic.withdrawOffer(regionId, region.world().getUID(), sender.getUniqueId());
                switch (result) {
                    case RealtyLogicImpl.WithdrawOfferResult.Success(var titleHolderId) -> {
                            sender.sendMessage(messages.messageFor("withdraw-offer.success",
                                    Placeholder.unparsed("region", regionId)));
                            notificationService.queueNotification(titleHolderId,
                                    messages.messageFor("notification.offer-withdrawn",
                                            Placeholder.unparsed("player", sender.getName()),
                                            Placeholder.unparsed("region", regionId)));
                    }
                    case RealtyLogicImpl.WithdrawOfferResult.NoOffer() ->
                            sender.sendMessage(messages.messageFor("withdraw-offer.no-offer",
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyLogicImpl.WithdrawOfferResult.OfferAccepted() ->
                            sender.sendMessage(messages.messageFor("withdraw-offer.accepted",
                                    Placeholder.unparsed("region", regionId)));
                }
            } catch (Exception ex) {
                sender.sendMessage(messages.messageFor("withdraw-offer.error",
                        Placeholder.unparsed("error", ex.getMessage())));
            }
        }, executorState.dbExec());
    }

    // ── /realty offer reject <player> [region] ──

    private void executeReject(@NotNull CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.sender().getSender() instanceof Player sender)) {
            return;
        }
        String playerName = ctx.get("player");
        WorldGuardRegion region = ctx.<WorldGuardRegion>optional("region")
                .orElseGet(() -> WorldGuardRegionResolver.resolveAtLocation(sender.getLocation()));
        if (region == null) {
            sender.sendMessage(messages.messageFor("error.no-region"));
            return;
        }
        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage(messages.messageFor("common.player-not-found",
                    Placeholder.unparsed("player", playerName)));
            return;
        }
        String regionId = region.region().getId();
        CompletableFuture.runAsync(() -> {
            try {
                RealtyLogicImpl.RejectOfferResult result = logic.rejectOffer(
                        regionId, region.world().getUID(), target.getUniqueId());
                switch (result) {
                    case RealtyLogicImpl.RejectOfferResult.Success ignored -> {
                        sender.sendMessage(messages.messageFor("reject-offer.success",
                                Placeholder.unparsed("player", playerName),
                                Placeholder.unparsed("region", regionId)));
                        notificationService.queueNotification(target.getUniqueId(),
                                messages.messageFor("notification.offer-rejected",
                                        Placeholder.unparsed("region", regionId)));
                    }
                    case RealtyLogicImpl.RejectOfferResult.NoOffer ignored ->
                            sender.sendMessage(messages.messageFor("reject-offer.no-offer",
                                    Placeholder.unparsed("player", playerName),
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyLogicImpl.RejectOfferResult.OfferAccepted ignored ->
                            sender.sendMessage(messages.messageFor("reject-offer.accepted",
                                    Placeholder.unparsed("region", regionId)));
                }
            } catch (Exception ex) {
                sender.sendMessage(messages.messageFor("reject-offer.error",
                        Placeholder.unparsed("error", ex.getMessage())));
            }
        }, executorState.dbExec());
    }

    // ── /realty offer rejectall [region] ──

    private void executeRejectAll(@NotNull CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.sender().getSender() instanceof Player sender)) {
            return;
        }
        WorldGuardRegion region = ctx.<WorldGuardRegion>optional("region")
                .orElseGet(() -> WorldGuardRegionResolver.resolveAtLocation(sender.getLocation()));
        if (region == null) {
            sender.sendMessage(messages.messageFor("error.no-region"));
            return;
        }
        String regionId = region.region().getId();
        CompletableFuture.runAsync(() -> {
            try {
                RealtyLogicImpl.RejectAllOffersResult result = logic.rejectAllOffers(
                        regionId, region.world().getUID());
                switch (result) {
                    case RealtyLogicImpl.RejectAllOffersResult.Success success -> {
                            sender.sendMessage(messages.messageFor("reject-offer.all-success",
                                    Placeholder.unparsed("count", String.valueOf(success.offererIds().size())),
                                    Placeholder.unparsed("region", regionId)));
                            Component notification = messages.messageFor("notification.offer-rejected",
                                    Placeholder.unparsed("region", regionId));
                            for (UUID offererId : success.offererIds()) {
                                notificationService.queueNotification(offererId, notification);
                            }
                    }
                    case RealtyLogicImpl.RejectAllOffersResult.NoSaleContract ignored ->
                            sender.sendMessage(messages.messageFor("reject-offer.no-sale-contract",
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyLogicImpl.RejectAllOffersResult.OfferAccepted ignored ->
                            sender.sendMessage(messages.messageFor("reject-offer.accepted",
                                    Placeholder.unparsed("region", regionId)));
                }
            } catch (Exception ex) {
                sender.sendMessage(messages.messageFor("reject-offer.error",
                        Placeholder.unparsed("error", ex.getMessage())));
            }
        }, executorState.dbExec());
    }

}
