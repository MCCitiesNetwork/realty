package io.github.md5sha256.realty.api.event;

import io.github.md5sha256.realty.api.WorldGuardRegion;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Called before a player buys a freehold region at its listed price.
 *
 * <p>Cancelling this event prevents the purchase entirely: no economy transfer
 * or database change occurs.</p>
 */
public class RegionBuyEvent extends RealtyRegionEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID buyerId;
    private boolean cancelled;

    public RegionBuyEvent(@NotNull WorldGuardRegion region, @NotNull UUID buyerId) {
        super(region);
        this.buyerId = buyerId;
    }

    /**
     * The player attempting to buy the region.
     */
    public @NotNull UUID getBuyerId() {
        return this.buyerId;
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
