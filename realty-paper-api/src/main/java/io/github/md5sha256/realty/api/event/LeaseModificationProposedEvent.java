package io.github.md5sha256.realty.api.event;

import io.github.md5sha256.realty.api.WorldGuardRegion;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Called after a lease modification has been proposed.
 *
 * <p>{@code active} is {@code true} when this is a landlord proposal that applies
 * on the next renewal, and {@code false} when the proposal awaits the landlord.</p>
 */
public class LeaseModificationProposedEvent extends RealtyRegionEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String proposerRole;
    private final UUID proposerId;
    private final UUID landlordId;
    private final UUID tenantId;
    private final boolean active;

    public LeaseModificationProposedEvent(@NotNull WorldGuardRegion region,
                                          @NotNull String proposerRole,
                                          @NotNull UUID proposerId,
                                          @NotNull UUID landlordId,
                                          @NotNull UUID tenantId,
                                          boolean active) {
        super(region);
        this.proposerRole = proposerRole;
        this.proposerId = proposerId;
        this.landlordId = landlordId;
        this.tenantId = tenantId;
        this.active = active;
    }

    /**
     * The role of the party that proposed the modification.
     */
    public @NotNull String getProposerRole() {
        return this.proposerRole;
    }

    /**
     * The player who proposed the modification.
     */
    public @NotNull UUID getProposerId() {
        return this.proposerId;
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
     * Whether the proposal is active (a landlord proposal applying on next renewal)
     * rather than awaiting the landlord.
     */
    public boolean isActive() {
        return this.active;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
