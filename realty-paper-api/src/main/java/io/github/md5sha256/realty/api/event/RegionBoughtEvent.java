package io.github.md5sha256.realty.api.event;

import io.github.md5sha256.realty.api.FeeQuote;
import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.api.WorldGuardRegion;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Called after a player has successfully bought a freehold region. The economy
 * transfer and ownership change have already been committed.
 */
public class RegionBoughtEvent extends RealtyRegionEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID buyerId;
    private final UUID previousTitleHolderId;
    private final double price;
    private final FeeQuote fees;

    /**
     * Creates the event for a sale that carried no fees.
     */
    public RegionBoughtEvent(@NotNull WorldGuardRegion region,
                             @NotNull UUID buyerId,
                             @Nullable UUID previousTitleHolderId,
                             double price) {
        this(region, buyerId, previousTitleHolderId, price, FeeQuote.none(price));
    }

    public RegionBoughtEvent(@NotNull WorldGuardRegion region,
                             @NotNull UUID buyerId,
                             @Nullable UUID previousTitleHolderId,
                             double price,
                             @NotNull FeeQuote fees) {
        super(region);
        this.buyerId = buyerId;
        this.previousTitleHolderId = previousTitleHolderId;
        this.price = price;
        this.fees = fees;
    }

    /**
     * The new title holder.
     */
    public @NotNull Party getNewTitleHolder() {
        return Party.personal(this.buyerId);
    }

    /**
     * The new title holder.
     *
     * @deprecated use {@link #getNewTitleHolder()}. Removed in 3.0.0.
     */
    @Deprecated(forRemoval = true)
    public @NotNull UUID getBuyerId() {
        return this.buyerId;
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
     * The price paid.
     */
    public double getPrice() {
        return this.price;
    }

    /**
     * The fees the sale carried: the fee lines, what the buyer paid in all and what the seller
     * received after fees. {@link FeeQuote#none(double)} when it carried none.
     */
    public @NotNull FeeQuote getFees() {
        return this.fees;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
