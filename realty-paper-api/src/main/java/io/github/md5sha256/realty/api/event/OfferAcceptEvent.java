package io.github.md5sha256.realty.api.event;

import io.github.md5sha256.realty.api.WorldGuardRegion;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Called before a title holder accepts a pending offer on a region.
 */
public class OfferAcceptEvent extends RealtyRegionEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID accepterId;
    private final UUID offererId;
    private boolean cancelled;

    public OfferAcceptEvent(@NotNull WorldGuardRegion region,
                            @NotNull UUID accepterId,
                            @NotNull UUID offererId) {
        super(region);
        this.accepterId = accepterId;
        this.offererId = offererId;
    }

    /**
     * The player accepting the offer.
     */
    public @NotNull UUID getAccepterId() {
        return this.accepterId;
    }

    /**
     * The player who made the offer.
     */
    public @NotNull UUID getOffererId() {
        return this.offererId;
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
