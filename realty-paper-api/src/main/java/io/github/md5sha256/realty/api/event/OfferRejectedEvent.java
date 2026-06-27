package io.github.md5sha256.realty.api.event;

import io.github.md5sha256.realty.api.WorldGuardRegion;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Called after a title holder has rejected a pending offer on a region.
 */
public class OfferRejectedEvent extends RealtyRegionEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID rejecterId;
    private final UUID offererId;

    public OfferRejectedEvent(@NotNull WorldGuardRegion region,
                              @NotNull UUID rejecterId,
                              @NotNull UUID offererId) {
        super(region);
        this.rejecterId = rejecterId;
        this.offererId = offererId;
    }

    /**
     * The player who rejected the offer.
     */
    public @NotNull UUID getRejecterId() {
        return this.rejecterId;
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
