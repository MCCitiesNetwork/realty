package io.github.md5sha256.realty.api.event;

import io.github.md5sha256.realty.api.WorldGuardRegion;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Called by the sweep when a scheduled termination actually ends the lease.
 */
public class LeaseTerminatedEvent extends RealtyRegionEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID tenantId;
    private final UUID landlordId;
    private final double refund;
    private final String terminatedByRole;

    public LeaseTerminatedEvent(@NotNull WorldGuardRegion region,
                                @NotNull UUID tenantId,
                                @NotNull UUID landlordId,
                                double refund,
                                @NotNull String terminatedByRole) {
        super(region);
        this.tenantId = tenantId;
        this.landlordId = landlordId;
        this.refund = refund;
        this.terminatedByRole = terminatedByRole;
    }

    /**
     * The tenant of the lease.
     */
    public @NotNull UUID getTenantId() {
        return this.tenantId;
    }

    /**
     * The landlord of the lease.
     */
    public @NotNull UUID getLandlordId() {
        return this.landlordId;
    }

    /**
     * The amount refunded to the tenant.
     */
    public double getRefund() {
        return this.refund;
    }

    /**
     * The role of the party that scheduled the termination.
     */
    public @NotNull String getTerminatedByRole() {
        return this.terminatedByRole;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
