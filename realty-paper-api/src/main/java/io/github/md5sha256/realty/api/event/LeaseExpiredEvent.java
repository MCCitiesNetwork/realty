package io.github.md5sha256.realty.api.event;

import io.github.md5sha256.realty.api.WorldGuardRegion;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Called after a lease has expired and the tenant has been removed from the
 * region. Fired by the periodic expiry task, so there is no acting player and
 * the event is not cancellable.
 */
public class LeaseExpiredEvent extends RealtyRegionEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID tenantId;
    private final UUID landlordId;

    public LeaseExpiredEvent(@NotNull WorldGuardRegion region,
                             @NotNull UUID tenantId,
                             @NotNull UUID landlordId) {
        super(region);
        this.tenantId = tenantId;
        this.landlordId = landlordId;
    }

    /**
     * The former tenant whose lease expired.
     */
    public @NotNull UUID getTenantId() {
        return this.tenantId;
    }

    /**
     * The landlord of the expired lease.
     */
    public @NotNull UUID getLandlordId() {
        return this.landlordId;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
