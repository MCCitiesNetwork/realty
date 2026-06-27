package io.github.md5sha256.realty.api.event;

import io.github.md5sha256.realty.api.WorldGuardRegion;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Called after a player has withdrawn their pending offer on a region.
 */
public class OfferWithdrawnEvent extends RealtyRegionEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID offererId;
    private final UUID titleHolderId;

    public OfferWithdrawnEvent(@NotNull WorldGuardRegion region,
                               @NotNull UUID offererId,
                               @Nullable UUID titleHolderId) {
        super(region);
        this.offererId = offererId;
        this.titleHolderId = titleHolderId;
    }

    /**
     * The player who withdrew the offer.
     */
    public @NotNull UUID getOffererId() {
        return this.offererId;
    }

    /**
     * The current title holder, or {@code null} if the region was held only by an
     * authority.
     */
    public @Nullable UUID getTitleHolderId() {
        return this.titleHolderId;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
