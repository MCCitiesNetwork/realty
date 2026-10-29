package io.github.md5sha256.realty.api.event;

import io.github.md5sha256.realty.api.WorldGuardRegion;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * Called after an auction has been created for a region.
 */
public class AuctionCreatedEvent extends RealtyRegionEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID auctioneerId;
    private final double minBid;
    private final double minBidStep;
    private final long biddingDurationSeconds;
    private final long paymentDurationSeconds;
    private final Instant biddingEndsAt;

    public AuctionCreatedEvent(@NotNull WorldGuardRegion region,
                               @NotNull UUID auctioneerId,
                               double minBid,
                               double minBidStep,
                               long biddingDurationSeconds,
                               long paymentDurationSeconds,
                               @NotNull Instant biddingEndsAt) {
        super(region);
        this.auctioneerId = auctioneerId;
        this.minBid = minBid;
        this.minBidStep = minBidStep;
        this.biddingDurationSeconds = biddingDurationSeconds;
        this.paymentDurationSeconds = paymentDurationSeconds;
        this.biddingEndsAt = biddingEndsAt;
    }

    /**
     * The player who created the auction.
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
     * How long the auction stays open after its start or its latest bid, in seconds.
     */
    public long getBiddingDurationSeconds() {
        return this.biddingDurationSeconds;
    }

    /**
     * How long the winner has to pay once bidding ends, in seconds.
     */
    public long getPaymentDurationSeconds() {
        return this.paymentDurationSeconds;
    }

    /**
     * When bidding ends if nobody bids. Each bid moves it later; see
     * {@link AuctionBidPlacedEvent#getBiddingEndsAt()}.
     */
    public @NotNull Instant getBiddingEndsAt() {
        return this.biddingEndsAt;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
