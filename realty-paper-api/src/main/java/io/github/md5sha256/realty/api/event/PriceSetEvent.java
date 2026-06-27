package io.github.md5sha256.realty.api.event;

import io.github.md5sha256.realty.api.WorldGuardRegion;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Called before the price of a region is set.
 */
public class PriceSetEvent extends RealtyRegionEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID actorId;
    private final double price;
    private boolean cancelled;

    public PriceSetEvent(@NotNull WorldGuardRegion region, @NotNull UUID actorId, double price) {
        super(region);
        this.actorId = actorId;
        this.price = price;
    }

    /**
     * The player setting the price.
     */
    public @NotNull UUID getActorId() {
        return this.actorId;
    }

    /**
     * The price being set.
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
