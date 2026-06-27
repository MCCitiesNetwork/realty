package io.github.md5sha256.realty.api.event;

import io.github.md5sha256.realty.api.WorldGuardRegion;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Called after a purchase offer has been recorded on a freehold region.
 */
public class OfferPlacedEvent extends RealtyRegionEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID offererId;
    private final UUID titleHolderId;
    private final double price;

    public OfferPlacedEvent(@NotNull WorldGuardRegion region, @NotNull UUID offererId,
                            @Nullable UUID titleHolderId, double price) {
        super(region);
        this.offererId = offererId;
        this.titleHolderId = titleHolderId;
        this.price = price;
    }

    /**
     * The player who made the offer.
     */
    public @NotNull UUID getOffererId() {
        return this.offererId;
    }

    /**
     * The current title holder who received the offer, or {@code null} if the
     * region is held only by an authority.
     */
    public @Nullable UUID getTitleHolderId() {
        return this.titleHolderId;
    }

    /**
     * The offered price.
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
