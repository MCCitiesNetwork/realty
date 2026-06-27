package io.github.md5sha256.realty.api.event;

import io.github.md5sha256.realty.api.WorldGuardRegion;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Called before a bid is placed on an auctioned region.
 */
public class AuctionBidEvent extends RealtyRegionEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID bidderId;
    private final double bidAmount;
    private boolean cancelled;

    public AuctionBidEvent(@NotNull WorldGuardRegion region,
                           @NotNull UUID bidderId,
                           double bidAmount) {
        super(region);
        this.bidderId = bidderId;
        this.bidAmount = bidAmount;
    }

    /**
     * The player placing the bid.
     */
    public @NotNull UUID getBidderId() {
        return this.bidderId;
    }

    /**
     * The amount being bid.
     */
    public double getBidAmount() {
        return this.bidAmount;
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
