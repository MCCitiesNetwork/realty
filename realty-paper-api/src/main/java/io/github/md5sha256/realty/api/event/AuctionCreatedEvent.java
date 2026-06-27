package io.github.md5sha256.realty.api.event;

import io.github.md5sha256.realty.api.WorldGuardRegion;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Called after an auction has been created for a region.
 */
public class AuctionCreatedEvent extends RealtyRegionEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID auctioneerId;
    private final double minBid;
    private final double minBidStep;

    public AuctionCreatedEvent(@NotNull WorldGuardRegion region,
                               @NotNull UUID auctioneerId,
                               double minBid,
                               double minBidStep) {
        super(region);
        this.auctioneerId = auctioneerId;
        this.minBid = minBid;
        this.minBidStep = minBidStep;
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

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
