package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.api.ExecutorState;
import io.github.md5sha256.realty.auth.ActorContexts;
import io.github.md5sha256.realty.api.CurrencyFormatter;
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
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.incendo.cloud.paper.util.sender.Source;

import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.flag.CommandFlag;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Handles {@code /realty buy [region] [--confirm]}.
 *
 * <p>Performs a fixed-price purchase at the listed price without requiring
 * approval from the current title holder. When the sale carries fees, the buyer is first shown
 * the breakdown and a link that runs the command again with {@code --confirm}.</p>
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

    /** Confirms a buy that carries fees, after the buyer has seen the breakdown. */
    static final CommandFlag<Void> CONFIRM_FLAG = CommandFlag.<Source>builder("confirm").build();

    /** The command the confirmation link runs: this buy again, confirmed. */
    static @NotNull String confirmCommand(@NotNull String regionId) {
        return "/realty buy " + regionId + " --confirm";
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
        if (ctx.flags().hasFlag(CONFIRM_FLAG)) {
            buy(sender, region);
            return;
        }
        // A sale with fees is shown first and bought only once confirmed; one without goes through.
        String regionId = region.region().getId();
        api.getFreeholdContract(regionId, region.world().getUID())
                .thenCompose(freehold -> freehold == null || freehold.price() == null
                        ? CompletableFuture.<FeePreview>completedFuture(null)
                        : FeePreview.of(api, region, sender.getUniqueId(), freehold.price()))
                .thenAcceptAsync(preview -> {
                    if (preview == null || !preview.hasFees()) {
                        buy(sender, region);
                        return;
                    }
                    sender.sendMessage(messages.messageFor(MessageKeys.FEES_BREAKDOWN_HEADER_BUY,
                                    Placeholder.unparsed("region", regionId),
                                    Placeholder.unparsed("price", CurrencyFormatter.format(preview.quote().price())))
                            .appendNewline()
                            .append(FeeQuoteFormatter.breakdown(preview.quote(), preview.buyerPlots(), messages,
                                    partyNames::display))
                            .appendNewline()
                            .append(messages.commandLink(MessageKeys.BUY_CONFIRM_FEES, confirmCommand(regionId))));
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
