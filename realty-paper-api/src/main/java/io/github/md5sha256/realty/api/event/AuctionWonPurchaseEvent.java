package io.github.md5sha256.realty.api.event;

import io.github.md5sha256.realty.api.FeeQuote;
import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.api.WorldGuardRegion;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Called after the winner of an auction has paid and ownership of the region has
 * transferred to them.
 */
public class AuctionWonPurchaseEvent extends RealtyRegionEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID winnerId;
    private final UUID previousTitleHolderId;
    private final double amount;
    private final FeeQuote fees;

    /**
     * Creates the event for a sale that carried no fees.
     */
    public AuctionWonPurchaseEvent(@NotNull WorldGuardRegion region,
                                   @NotNull UUID winnerId,
                                   @Nullable UUID previousTitleHolderId,
                                   double amount) {
        this(region, winnerId, previousTitleHolderId, amount, FeeQuote.none(amount));
    }

    public AuctionWonPurchaseEvent(@NotNull WorldGuardRegion region,
                                   @NotNull UUID winnerId,
                                   @Nullable UUID previousTitleHolderId,
                                   double amount,
                                   @NotNull FeeQuote fees) {
        super(region);
        this.winnerId = winnerId;
        this.previousTitleHolderId = previousTitleHolderId;
        this.amount = amount;
        this.fees = fees;
    }

    /**
     * The player who won the auction and is now the new title holder.
     */
    public @NotNull Party getNewTitleHolder() {
        return Party.personal(this.winnerId);
    }

    /**
     * The player who won the auction and is now the new title holder.
     *
     * @deprecated use {@link #getNewTitleHolder()}. Removed in 3.0.0.
     */
    @Deprecated(forRemoval = true)
    public @NotNull UUID getWinnerId() {
        return this.winnerId;
    }

    /**
     * The previous title holder who received the payment, or {@code null} if the
     * region was previously held only by an authority.
     */
    public @Nullable Party getPreviousTitleHolder() {
        return this.previousTitleHolderId == null ? null : Party.personal(this.previousTitleHolderId);
    }

    /**
     * The previous title holder who received the payment, or {@code null} if the
     * region was previously held only by an authority.
     *
     * @deprecated use {@link #getPreviousTitleHolder()}. Removed in 3.0.0.
     */
    @Deprecated(forRemoval = true)
    public @Nullable UUID getPreviousTitleHolderId() {
        return this.previousTitleHolderId;
    }

    /**
     * The sale price: the winning bid or the accepted offer, without any fees. The buyer may have
     * paid it in several instalments, and buyer-paid fees on top.
     */
    public double getAmount() {
        return this.amount;
    }

    /**
     * The fees the sale carried: the fee lines, what the buyer paid in all and what the seller
     * received after fees. {@link FeeQuote#none(double)} when it carried none.
     */
    public @NotNull FeeQuote getFees() {
        return this.fees;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
