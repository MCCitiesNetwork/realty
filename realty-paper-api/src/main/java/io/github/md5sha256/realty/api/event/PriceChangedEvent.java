package io.github.md5sha256.realty.api.event;

import io.github.md5sha256.realty.api.WorldGuardRegion;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Called after the price of a region has been changed.
 */
public class PriceChangedEvent extends RealtyRegionEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final double price;

    public PriceChangedEvent(@NotNull WorldGuardRegion region, double price) {
        super(region);
        this.price = price;
    }

    /**
     * The new price.
     */
    public double getPrice() {
        return this.price;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
