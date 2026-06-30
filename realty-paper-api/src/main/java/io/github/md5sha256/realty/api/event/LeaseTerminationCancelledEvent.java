package io.github.md5sha256.realty.api.event;

import io.github.md5sha256.realty.api.WorldGuardRegion;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Called after a scheduled lease termination has been cancelled before taking effect.
 */
public class LeaseTerminationCancelledEvent extends RealtyRegionEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID landlordId;
    private final UUID tenantId;
    private final String terminatedByRole;

    public LeaseTerminationCancelledEvent(@NotNull WorldGuardRegion region,
                                          @NotNull UUID landlordId,
                                          @NotNull UUID tenantId,
                                          @NotNull String terminatedByRole) {
        super(region);
        this.landlordId = landlordId;
        this.tenantId = tenantId;
        this.terminatedByRole = terminatedByRole;
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
     * The role of the party that had scheduled the termination.
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
