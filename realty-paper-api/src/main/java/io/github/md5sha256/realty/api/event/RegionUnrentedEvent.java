package io.github.md5sha256.realty.api.event;

import io.github.md5sha256.realty.api.WorldGuardRegion;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Called after a tenant has successfully ended a lease early. The refund and
 * tenancy change have already been committed.
 */
public class RegionUnrentedEvent extends RealtyRegionEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID tenantId;
    private final UUID landlordId;
    private final double refund;

    public RegionUnrentedEvent(@NotNull WorldGuardRegion region,
                               @NotNull UUID tenantId,
                               @NotNull UUID landlordId,
                               double refund) {
        super(region);
        this.tenantId = tenantId;
        this.landlordId = landlordId;
        this.refund = refund;
    }

    /**
     * The former tenant.
     */
    public @NotNull UUID getTenantId() {
        return this.tenantId;
    }

    /**
     * The landlord who paid the refund.
     */
    public @NotNull UUID getLandlordId() {
        return this.landlordId;
    }

    /**
     * The prorated refund paid to the tenant.
     */
    public double getRefund() {
        return this.refund;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
