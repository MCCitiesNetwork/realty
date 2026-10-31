package io.github.md5sha256.realty.api.event;

import io.github.md5sha256.realty.api.WorldGuardRegion;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Called after a title holder has accepted a pending offer on a region.
 */
public class OfferAcceptedEvent extends RealtyRegionEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID accepterId;
    private final UUID offererId;
    private final double price;
    private final double totalDue;
    private final @Nullable Instant paymentDeadline;

    /**
     * @param price           the offer's price
     * @param totalDue        what the offerer owes: the price plus the buyer's fees, as locked on acceptance
     * @param paymentDeadline when the offerer must have paid by
     */
    public OfferAcceptedEvent(@NotNull WorldGuardRegion region,
                              @NotNull UUID accepterId,
                              @NotNull UUID offererId,
                              double price,
                              double totalDue,
                              @NotNull Instant paymentDeadline) {
        super(region);
        this.accepterId = accepterId;
        this.offererId = offererId;
        this.price = price;
        this.totalDue = totalDue;
        this.paymentDeadline = paymentDeadline;
    }

    /**
     * @deprecated says nothing of what the offerer owes; use the constructor that does.
     */
    @Deprecated
    public OfferAcceptedEvent(@NotNull WorldGuardRegion region,
                              @NotNull UUID accepterId,
                              @NotNull UUID offererId) {
        super(region);
        this.accepterId = accepterId;
        this.offererId = offererId;
        this.price = 0;
        this.totalDue = 0;
        this.paymentDeadline = null;
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

    /**
     * The offer's price, or 0 when the event was built without it.
     */
    public double getPrice() {
        return this.price;
    }

    /**
     * What the offerer owes: the price plus the buyer's fees, as locked on acceptance; 0 when the
     * event was built without it.
     */
    public double getTotalDue() {
        return this.totalDue;
    }

    /**
     * When the offerer must have paid by, or empty when the event was built without it.
     */
    public @NotNull Optional<Instant> getPaymentDeadline() {
        return Optional.ofNullable(this.paymentDeadline);
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
