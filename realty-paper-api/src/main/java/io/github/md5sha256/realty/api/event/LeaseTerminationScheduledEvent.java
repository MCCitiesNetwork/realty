package io.github.md5sha256.realty.api.event;

import io.github.md5sha256.realty.api.WorldGuardRegion;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Called after a lease termination has been scheduled to take effect at a future date.
 */
public class LeaseTerminationScheduledEvent extends RealtyRegionEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID landlordId;
    private final UUID tenantId;
    private final String terminatedByRole;
    private final LocalDateTime effectiveDate;
    private final double charged;

    public LeaseTerminationScheduledEvent(@NotNull WorldGuardRegion region,
                                          @NotNull UUID landlordId,
                                          @NotNull UUID tenantId,
                                          @NotNull String terminatedByRole,
                                          @NotNull LocalDateTime effectiveDate,
                                          double charged) {
        super(region);
        this.landlordId = landlordId;
        this.tenantId = tenantId;
        this.terminatedByRole = terminatedByRole;
        this.effectiveDate = effectiveDate;
        this.charged = charged;
    }

    /**
     * The landlord of the lease.
     */
    public @NotNull UUID getLandlordId() {
        return this.landlordId;
    }

    /**
     * The tenant of the lease.
     */
    public @NotNull UUID getTenantId() {
        return this.tenantId;
    }

    /**
     * The role of the party that scheduled the termination.
     */
    public @NotNull String getTerminatedByRole() {
        return this.terminatedByRole;
    }

    /**
     * The date the termination takes effect.
     */
    public @NotNull LocalDateTime getEffectiveDate() {
        return this.effectiveDate;
    }

    /**
     * The amount charged for scheduling the termination.
     */
    public double getCharged() {
        return this.charged;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
