package io.github.md5sha256.realty.api.event;

import io.github.md5sha256.realty.api.WorldGuardRegion;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Called before a player places a purchase offer on a freehold region.
 *
 * <p>Cancelling prevents the offer from being recorded.</p>
 */
public class OfferPlaceEvent extends RealtyRegionEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID offererId;
    private final double price;
    private boolean cancelled;

    public OfferPlaceEvent(@NotNull WorldGuardRegion region, @NotNull UUID offererId, double price) {
        super(region);
        this.offererId = offererId;
        this.price = price;
    }

    /**
     * The player making the offer.
     */
    public @NotNull UUID getOffererId() {
        return this.offererId;
    }

    /**
     * The offered price.
     */
    public double getPrice() {
        return this.price;
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
