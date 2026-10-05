package io.github.md5sha256.realty.api.event;

import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.api.WorldGuardRegion;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Called after an accepted offer has been fully paid and ownership of the region
 * has transferred to the offerer.
 */
public class OfferPurchaseCompletedEvent extends RealtyRegionEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID offererId;
    private final UUID previousTitleHolderId;
    private final double amount;

    public OfferPurchaseCompletedEvent(@NotNull WorldGuardRegion region,
                                       @NotNull UUID offererId,
                                       @Nullable UUID previousTitleHolderId,
                                       double amount) {
        super(region);
        this.offererId = offererId;
        this.previousTitleHolderId = previousTitleHolderId;
        this.amount = amount;
    }

    /**
     * The player who made the offer and is now the new title holder.
     */
    public @NotNull Party getNewTitleHolder() {
        return Party.personal(this.offererId);
    }

    /**
     * The player who made the offer and is now the new title holder.
     *
     * @deprecated use {@link #getNewTitleHolder()}. Removed in 3.0.0.
     */
    @Deprecated(forRemoval = true)
    public @NotNull UUID getOffererId() {
        return this.offererId;
    }

    /**
     * The previous title holder who received the payment, or {@code null} if the
     * region was previously held only by an authority.
     */
    public @Nullable Party getPreviousTitleHolder() {
        return this.previousTitleHolderId == null ? null : Party.personal(this.previousTitleHolderId);
    }

    /**
     * The previous title holder who received the payment, or {@code null} if the
     * region was previously held only by an authority.
     *
     * @deprecated use {@link #getPreviousTitleHolder()}. Removed in 3.0.0.
     */
    @Deprecated(forRemoval = true)
    public @Nullable UUID getPreviousTitleHolderId() {
        return this.previousTitleHolderId;
    }

    /**
     * The amount paid.
     */
    public double getAmount() {
        return this.amount;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
