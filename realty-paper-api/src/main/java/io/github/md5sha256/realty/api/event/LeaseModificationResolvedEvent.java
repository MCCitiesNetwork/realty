package io.github.md5sha256.realty.api.event;

import io.github.md5sha256.realty.api.WorldGuardRegion;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Called after a proposed lease modification has been resolved.
 *
 * <p>The {@code resolution} is one of {@code "ACCEPTED"}, {@code "REJECTED"} or
 * {@code "WITHDRAWN"}.</p>
 */
public class LeaseModificationResolvedEvent extends RealtyRegionEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String resolution;
    private final String proposerRole;
    private final UUID landlordId;
    private final UUID tenantId;

    public LeaseModificationResolvedEvent(@NotNull WorldGuardRegion region,
                                          @NotNull String resolution,
                                          @NotNull String proposerRole,
                                          @NotNull UUID landlordId,
                                          @NotNull UUID tenantId) {
        super(region);
        this.resolution = resolution;
        this.proposerRole = proposerRole;
        this.landlordId = landlordId;
        this.tenantId = tenantId;
    }

    /**
     * The resolution outcome: {@code "ACCEPTED"}, {@code "REJECTED"} or {@code "WITHDRAWN"}.
     */
    public @NotNull String getResolution() {
        return this.resolution;
    }

    /**
     * The role of the party that proposed the modification.
     */
    public @NotNull String getProposerRole() {
        return this.proposerRole;
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

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
