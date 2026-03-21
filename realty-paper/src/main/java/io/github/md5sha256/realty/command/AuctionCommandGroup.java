package io.github.md5sha256.realty.command;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import io.github.md5sha256.realty.api.NotificationService;
import io.github.md5sha256.realty.command.util.DurationParser;
import io.github.md5sha256.realty.command.util.WorldGuardRegion;
import io.github.md5sha256.realty.command.util.WorldGuardRegionParser;
import io.github.md5sha256.realty.command.util.WorldGuardRegionResolver;
import io.github.md5sha256.realty.database.RealtyLogicImpl;
import io.github.md5sha256.realty.database.RealtyLogicImpl.CreateAuctionResult;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.util.ExecutorState;
import io.papermc.paper.command.brigadier.CommandSourceStack;
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
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

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
        @NotNull MessageContainer messages
) implements CustomCommandBean {

    @Override
    public @NotNull List<Command<CommandSourceStack>> commands(@NotNull CommandManager<CommandSourceStack> manager) {
        var base = manager.commandBuilder("realty").literal("auction");
        return List.of(
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
                            sender.sendMessage(messages.messageFor("auction.success",
                                    Placeholder.unparsed("region", regionId)));
                    case CreateAuctionResult.NotSanctioned ignored ->
                            sender.sendMessage(messages.messageFor("auction.not-sanctioned",
                                    Placeholder.unparsed("region", regionId)));
                    case CreateAuctionResult.NoSaleContract ignored ->
                            sender.sendMessage(messages.messageFor("auction.no-sale-contract",
                                    Placeholder.unparsed("region", regionId)));
                }
            } catch (Exception ex) {
                sender.sendMessage(messages.messageFor("auction.error",
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
            sender.sendMessage(messages.messageFor("error.no-region"));
            return;
        }
        String regionId = region.region().getId();
        CompletableFuture.runAsync(() -> {
            try {
                RealtyLogicImpl.CancelAuctionResult result = logic.cancelAuction(regionId, region.world().getUID());
                if (result.deleted() == 0) {
                    sender.sendMessage(messages.messageFor("cancel-auction.no-auction"));
                    return;
                }
                sender.sendMessage(messages.messageFor("cancel-auction.success",
                        Placeholder.unparsed("region", regionId)));
                for (UUID bidderId : result.bidderIds()) {
                    notificationService.queueNotification(bidderId,
                            messages.messageFor("notification.auction-cancelled",
                                    Placeholder.unparsed("region", regionId)));
                }
            } catch (Exception ex) {
                sender.sendMessage(messages.messageFor("cancel-auction.error",
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
                            sender.sendMessage(messages.messageFor("bid.success",
                                    Placeholder.unparsed("amount", String.valueOf(bidAmount)),
                                    Placeholder.unparsed("region", regionId)));
                            if (success.previousBidderId() != null) {
                                notificationService.queueNotification(success.previousBidderId(),
                                        messages.messageFor("notification.outbid",
                                                Placeholder.unparsed("region", regionId),
                                                Placeholder.unparsed("amount", String.valueOf(bidAmount))));
                            }
                    }
                    case RealtyLogicImpl.BidResult.NoAuction ignored ->
                            sender.sendMessage(messages.messageFor("bid.no-auction"));
                    case RealtyLogicImpl.BidResult.BidTooLowMinimum r ->
                            sender.sendMessage(messages.messageFor("bid.too-low-minimum",
                                    Placeholder.unparsed("amount", String.valueOf(r.minBid()))));
                    case RealtyLogicImpl.BidResult.BidTooLowCurrent r ->
                            sender.sendMessage(messages.messageFor("bid.too-low-current",
                                    Placeholder.unparsed("amount", String.valueOf(r.currentHighest()))));
                }
            } catch (Exception ex) {
                sender.sendMessage(messages.messageFor("bid.error",
                        Placeholder.unparsed("error", ex.getMessage())));
            }
        }, executorState.dbExec());
    }

    // ── /realty auction paybid <amount> <region> ──

    private void executePayBid(@NotNull CommandContext<CommandSourceStack> ctx) {
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
            sender.sendMessage(messages.messageFor("pay-bid.insufficient-funds",
                    Placeholder.unparsed("balance", String.valueOf(balance))));
            return;
        }
        EconomyResponse response = economy.withdrawPlayer(sender, amount);
        if (!response.transactionSuccess()) {
            sender.sendMessage(messages.messageFor("pay-bid.payment-failed",
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
                        sender.sendMessage(messages.messageFor("pay-bid.success",
                                Placeholder.unparsed("amount", String.valueOf(amount)),
                                Placeholder.unparsed("region", regionId),
                                Placeholder.unparsed("total", String.valueOf(success.newTotal())),
                                Placeholder.unparsed("remaining", String.valueOf(success.remaining()))));
                        yield null;
                    }
                    case RealtyLogicImpl.PayBidResult.FullyPaid fullyPaid -> {
                        sender.sendMessage(messages.messageFor("pay-bid.fully-paid",
                                Placeholder.unparsed("amount", String.valueOf(amount)),
                                Placeholder.unparsed("region", regionId)));
                        yield fullyPaid;
                    }
                    case RealtyLogicImpl.PayBidResult.NoPaymentRecord ignored -> {
                        sender.sendMessage(messages.messageFor("pay-bid.no-payment-record",
                                Placeholder.unparsed("region", regionId)));
                        yield null;
                    }
                    case RealtyLogicImpl.PayBidResult.PaymentExpired ignored -> {
                        sender.sendMessage(messages.messageFor("pay-bid.payment-expired",
                                Placeholder.unparsed("region", regionId)));
                        yield null;
                    }
                    case RealtyLogicImpl.PayBidResult.ExceedsAmountOwed exceeds -> {
                        sender.sendMessage(messages.messageFor("pay-bid.exceeds-owed",
                                Placeholder.unparsed("amount", String.valueOf(amount)),
                                Placeholder.unparsed("owed", String.valueOf(exceeds.amountOwed())),
                                Placeholder.unparsed("region", regionId)));
                        yield null;
                    }
                };
            } catch (Exception ex) {
                sender.sendMessage(messages.messageFor("pay-bid.error",
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
                    sender.sendMessage(messages.messageFor("pay-bid.transfer-failed"));
                    return;
                }
                ProtectedRegion protectedRegion = region.region();
                protectedRegion.getOwners().clear();
                protectedRegion.getOwners().addPlayer(sender.getUniqueId());
                protectedRegion.getMembers().clear();
                sender.sendMessage(messages.messageFor("pay-bid.transfer-success",
                        Placeholder.unparsed("region", regionId)));
                notificationService.queueNotification(fullyPaid.authorityId(),
                        messages.messageFor("notification.ownership-transferred",
                                Placeholder.unparsed("player", sender.getName()),
                                Placeholder.unparsed("region", regionId)));
            }
        }, executorState.mainThreadExec());
    }

}
