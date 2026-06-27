package io.github.md5sha256.realty.api.event;

import io.github.md5sha256.realty.api.WorldGuardRegion;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Called after a player has successfully rented a leasehold region. The economy
 * transfer and tenancy change have already been committed.
 */
public class RegionRentedEvent extends RealtyRegionEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID tenantId;
    private final UUID landlordId;
    private final double price;
    private final long durationSeconds;

    public RegionRentedEvent(@NotNull WorldGuardRegion region,
                             @NotNull UUID tenantId,
                             @NotNull UUID landlordId,
                             double price,
                             long durationSeconds) {
        super(region);
        this.tenantId = tenantId;
        this.landlordId = landlordId;
        this.price = price;
        this.durationSeconds = durationSeconds;
    }

    /**
     * The new tenant.
     */
    public @NotNull UUID getTenantId() {
        return this.tenantId;
    }

    /**
     * The landlord who received the payment.
     */
    public @NotNull UUID getLandlordId() {
        return this.landlordId;
    }

    /**
     * The rent paid.
     */
    public double getPrice() {
        return this.price;
    }

    /**
     * The lease duration in seconds.
     */
    public long getDurationSeconds() {
        return this.durationSeconds;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
