package io.github.md5sha256.realty.api.event;

import io.github.md5sha256.realty.api.WorldGuardRegion;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Called after a title holder has accepted a pending offer on a region.
 */
public class OfferAcceptedEvent extends RealtyRegionEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID accepterId;
    private final UUID offererId;

    public OfferAcceptedEvent(@NotNull WorldGuardRegion region,
                              @NotNull UUID accepterId,
                              @NotNull UUID offererId) {
        super(region);
        this.accepterId = accepterId;
        this.offererId = offererId;
    }

    /**
     * The player who accepted the offer.
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
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
