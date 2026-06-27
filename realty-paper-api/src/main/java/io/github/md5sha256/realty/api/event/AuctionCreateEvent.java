package io.github.md5sha256.realty.api.event;

import io.github.md5sha256.realty.api.WorldGuardRegion;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Called before an auction is created for a region.
 */
public class AuctionCreateEvent extends RealtyRegionEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID auctioneerId;
    private final double minBid;
    private final double minBidStep;
    private final long biddingDurationSeconds;
    private final long paymentDurationSeconds;
    private boolean cancelled;

    public AuctionCreateEvent(@NotNull WorldGuardRegion region,
                              @NotNull UUID auctioneerId,
                              double minBid,
                              double minBidStep,
                              long biddingDurationSeconds,
                              long paymentDurationSeconds) {
        super(region);
        this.auctioneerId = auctioneerId;
        this.minBid = minBid;
        this.minBidStep = minBidStep;
        this.biddingDurationSeconds = biddingDurationSeconds;
        this.paymentDurationSeconds = paymentDurationSeconds;
    }

    /**
     * The player creating the auction.
     */
    public @NotNull UUID getAuctioneerId() {
        return this.auctioneerId;
    }

    /**
     * The minimum opening bid.
     */
    public double getMinBid() {
        return this.minBid;
    }

    /**
     * The minimum increment between bids.
     */
    public double getMinBidStep() {
        return this.minBidStep;
    }

    /**
     * The duration of the bidding period in seconds.
     */
    public long getBiddingDurationSeconds() {
        return this.biddingDurationSeconds;
    }

    /**
     * The duration of the payment period in seconds.
     */
    public long getPaymentDurationSeconds() {
        return this.paymentDurationSeconds;
    }

    @Override
    public boolean isCancelled() {
        return this.cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
