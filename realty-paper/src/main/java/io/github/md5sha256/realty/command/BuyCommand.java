package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.api.ExecutorState;
import io.github.md5sha256.realty.auth.ActorContexts;
import io.github.md5sha256.realty.api.CurrencyFormatter;
import io.github.md5sha256.realty.api.Money;
import io.github.md5sha256.realty.api.RealtyPaperApi;
import io.github.md5sha256.realty.api.WorldGuardRegion;
import io.github.md5sha256.realty.api.event.RegionBoughtEvent;
import io.github.md5sha256.realty.api.event.RegionBuyEvent;
import io.github.md5sha256.realty.command.util.FeePreview;
import io.github.md5sha256.realty.command.util.FeeQuoteFormatter;
import io.github.md5sha256.realty.command.util.RegionOrFlagParser;
import io.github.md5sha256.realty.command.util.WorldGuardRegionResolver;
import io.github.md5sha256.realty.event.RealtyEventDispatch;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.localisation.MessageKeys;
import io.github.md5sha256.realty.util.PartyNames;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.incendo.cloud.paper.util.sender.Source;

import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.flag.CommandFlag;
import org.incendo.cloud.parser.standard.DoubleParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Handles {@code /realty buy [region] [--confirm]}.
 *
 * <p>Performs a fixed-price purchase at the listed price without requiring
 * approval from the current title holder. When the sale carries fees, the buyer is first shown
 * the breakdown and a link that runs the command again with {@code --confirm <total>}, the total
 * they saw. The confirmed buy looks at the fees again, and buys only if the total is still the
 * same; otherwise it shows the new breakdown and a fresh link.</p>
 *
 * <p>Permission: {@code realty.command.buy}.</p>
 */
public record BuyCommand(
        @NotNull RealtyPaperApi api,
        @NotNull ActorContexts actors,
        @NotNull ExecutorState executorState,
        @NotNull MessageContainer messages,
        @NotNull RealtyEventDispatch events,
        @NotNull PartyNames partyNames
) implements CustomCommandBean.Single {

    @Override
    public @NotNull Command<? extends Source> command(@NotNull Command.Builder<Source> builder) {
        return builder
                .literal("buy")
                .permission("realty.command.buy")
                .optional("region", RegionOrFlagParser.regionOrFlag())
                .flag(CONFIRM_FLAG)
                .handler(this::execute)
                .build();
    }

    /** Confirms a buy that carries fees at the total the buyer was shown. */
    static final CommandFlag<Double> CONFIRM_FLAG = CommandFlag.<Source>builder("confirm")
            .withComponent(DoubleParser.doubleParser(0, Double.MAX_VALUE))
            .build();

    /** The command the confirmation link runs: this buy again, confirmed at {@code total}. */
    static @NotNull String confirmCommand(@NotNull String regionId, double total) {
        return "/realty buy " + regionId + " --confirm " + Money.format(total);
    }

    /** What a buy does once the fees are looked up. */
    enum Step {
        /** Buy now. */
        BUY,
        /** Show the breakdown and a link to confirm it. */
        SHOW,
        /** The total confirmed is no longer the total: show the new one and a fresh link. */
        SHOW_CHANGED
    }

    /**
     * @param confirmedTotal the total of {@code --confirm}, or null when the buyer has not confirmed
     * @param preview        the fees as they stand now
     */
    static @NotNull Step step(@Nullable Double confirmedTotal, @NotNull FeePreview preview) {
        if (confirmedTotal == null) {
            return preview.hasFees() ? Step.SHOW : Step.BUY;
        }
        return Money.cents(confirmedTotal) == Money.cents(preview.quote().buyerTotal()) ? Step.BUY : Step.SHOW_CHANGED;
    }

    /** The post-event for a completed buy, carrying the fees the sale paid. */
    static @NotNull RegionBoughtEvent boughtEvent(@NotNull WorldGuardRegion region,
                                                  @NotNull java.util.UUID buyerId,
                                                  @NotNull RealtyPaperApi.BuyResult.Success success) {
        return new RegionBoughtEvent(region, buyerId,
                Party.playerUuidOf(success.previousTitleHolder()).orElse(null), success.price(),
                success.fees());
    }

    private void execute(@NotNull CommandContext<Source> ctx) {
        if (!(ctx.sender().source() instanceof Player sender)) {
            ctx.sender().source().sendMessage(messages.messageFor(MessageKeys.COMMON_PLAYERS_ONLY));
            return;
        }
        WorldGuardRegion region = ctx.<Optional<WorldGuardRegion>>optional("region")
                .flatMap(Function.identity())
                .orElseGet(() -> WorldGuardRegionResolver.resolveAtLocation(sender.getLocation()));
        if (region == null) {
            sender.sendMessage(messages.messageFor(MessageKeys.ERROR_NO_REGION));
            return;
        }
        Double confirmedTotal = ctx.flags().getValue(CONFIRM_FLAG, null);
        // A sale with fees is shown first and bought only once confirmed at the total shown; one
        // without goes through. A confirmed buy is looked at again, in case the fees changed.
        String regionId = region.region().getId();
        api.getFreeholdContract(regionId, region.world().getUID())
                .thenCompose(freehold -> freehold == null || freehold.price() == null
                        ? CompletableFuture.<FeePreview>completedFuture(null)
                        : FeePreview.of(api, region, sender.getUniqueId(), freehold.price()))
                .thenAcceptAsync(preview -> {
                    // No asking price: the buy itself says why it cannot go ahead.
                    Step step = preview == null ? Step.BUY : step(confirmedTotal, preview);
                    if (step == Step.BUY) {
                        buy(sender, region);
                        return;
                    }
                    Component breakdown = messages.messageFor(MessageKeys.FEES_BREAKDOWN_HEADER_BUY,
                                    Placeholder.unparsed("region", regionId),
                                    Placeholder.unparsed("price", CurrencyFormatter.format(preview.quote().price())))
                            .appendNewline()
                            .append(FeeQuoteFormatter.breakdown(preview.quote(), preview.buyerPlots(), messages,
                                    partyNames::display))
                            .appendNewline()
                            .append(messages.commandLink(MessageKeys.BUY_CONFIRM_FEES,
                                    confirmCommand(regionId, preview.quote().buyerTotal())));
                    sender.sendMessage(step == Step.SHOW_CHANGED
                            ? messages.messageFor(MessageKeys.BUY_TOTAL_CHANGED).appendNewline().append(breakdown)
                            : breakdown);
                }, executorState.mainThreadExec())
                .exceptionally(ex -> {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    sender.sendMessage(messages.messageFor(MessageKeys.BUY_ERROR,
                            Placeholder.unparsed("error", String.valueOf(cause.getMessage()))));
                    return null;
                });
    }

    private void buy(@NotNull Player sender, @NotNull WorldGuardRegion region) {
        // Cancellable pre-event (main thread); a veto stops the action before the API is called.
        if (!events.fireSync(new RegionBuyEvent(region, sender.getUniqueId()))) {
            sender.sendMessage(messages.messageFor(MessageKeys.COMMON_ACTION_CANCELLED));
            return;
        }
        boolean bypassConflict = sender.hasPermission("realty.bypass.conflict-of-interest");
        // The context names the authority when the buyer acts for it: the authority may not buy its own land.
        CompletableFuture.supplyAsync(() -> actors.forRegion(sender, false, region), executorState.dbExec())
                .thenComposeAsync(buyer -> api.buy(region, buyer, bypassConflict), executorState.mainThreadExec())
                .thenAccept(result -> {
            switch (result) {
                case RealtyPaperApi.BuyResult.Success success -> {
                    sender.sendMessage(messages.messageFor(MessageKeys.BUY_SUCCESS,
                            Placeholder.unparsed("price", CurrencyFormatter.format(success.price())),
                            Placeholder.unparsed("region", success.regionId())));
                    // Post-event; fireSync hops to the main thread. RegionNotificationListener notifies the seller.
                    events.fireSync(boughtEvent(region, sender.getUniqueId(), success));
                }
                case RealtyPaperApi.BuyResult.NoFreeholdContract noContract ->
                        sender.sendMessage(messages.messageFor(MessageKeys.BUY_NO_FREEHOLD_CONTRACT,
                                Placeholder.unparsed("region", noContract.regionId())));
                case RealtyPaperApi.BuyResult.NotForSale notForSale ->
                        sender.sendMessage(messages.messageFor(MessageKeys.BUY_NOT_FOR_SALE,
                                Placeholder.unparsed("region", notForSale.regionId())));
                case RealtyPaperApi.BuyResult.IsAuthority ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.BUY_IS_AUTHORITY));
                case RealtyPaperApi.BuyResult.IsTitleHolder ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.BUY_IS_TITLE_HOLDER));
                case RealtyPaperApi.BuyResult.InsufficientFunds insufficient ->
                        sender.sendMessage(messages.messageFor(MessageKeys.BUY_INSUFFICIENT_FUNDS,
                                Placeholder.unparsed("price", CurrencyFormatter.format(insufficient.price())),
                                Placeholder.unparsed("balance", CurrencyFormatter.format(insufficient.balance()))));
                case RealtyPaperApi.BuyResult.PaymentFailed failed ->
                        sender.sendMessage(messages.messageFor(MessageKeys.BUY_PAYMENT_FAILED,
                                Placeholder.unparsed("error", failed.error())));
                case RealtyPaperApi.BuyResult.TransferFailed transferFailed ->
                        sender.sendMessage(messages.messageFor(MessageKeys.BUY_TRANSFER_FAILED,
                                Placeholder.unparsed("region", transferFailed.regionId())));
                case RealtyPaperApi.BuyResult.Error error ->
                        sender.sendMessage(messages.messageFor(MessageKeys.BUY_ERROR,
                                Placeholder.unparsed("error", error.message())));
            }
        }).exceptionally(ex -> {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            sender.sendMessage(messages.messageFor(MessageKeys.BUY_ERROR,
                    Placeholder.unparsed("error", String.valueOf(cause.getMessage()))));
            return null;
        });
    }

}
