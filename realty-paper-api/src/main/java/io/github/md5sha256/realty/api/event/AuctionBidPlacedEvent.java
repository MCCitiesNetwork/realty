package io.github.md5sha256.realty.api.event;

import io.github.md5sha256.realty.api.WorldGuardRegion;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Called after a bid has been placed on an auctioned region.
 */
public class AuctionBidPlacedEvent extends RealtyRegionEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID bidderId;
    private final double bidAmount;

    public AuctionBidPlacedEvent(@NotNull WorldGuardRegion region,
                                 @NotNull UUID bidderId,
                                 double bidAmount) {
        super(region);
        this.bidderId = bidderId;
        this.bidAmount = bidAmount;
    }

    /**
     * The player who placed the bid.
     */
    public @NotNull UUID getBidderId() {
        return this.bidderId;
    }

    /**
     * The amount bid.
     */
    public double getBidAmount() {
        return this.bidAmount;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
