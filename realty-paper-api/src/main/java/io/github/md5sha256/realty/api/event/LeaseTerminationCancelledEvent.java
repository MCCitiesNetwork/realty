package io.github.md5sha256.realty.api.event;

import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.api.WorldGuardRegion;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Called after a scheduled lease termination has been cancelled before taking effect.
 */
public class LeaseTerminationCancelledEvent extends RealtyRegionEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Party landlord;
    private final UUID tenantId;
    private final String terminatedByRole;

    public LeaseTerminationCancelledEvent(@NotNull WorldGuardRegion region,
                                          @NotNull Party landlord,
                                          @Nullable UUID tenantId,
                                          @NotNull String terminatedByRole) {
        super(region);
        this.landlord = landlord;
        this.tenantId = tenantId;
        this.terminatedByRole = terminatedByRole;
    }

    /**
     * The landlord of the lease.
     */
    public @NotNull Party getLandlord() {
        return this.landlord;
    }

    /**
     * The landlord of the lease.
     *
     * @deprecated use {@link #getLandlord()}; {@code null} when the landlord is not a player.
     * Removed in 3.0.0.
     */
    @Deprecated(forRemoval = true)
    public @Nullable UUID getLandlordId() {
        return Party.playerUuidOf(this.landlord).orElse(null);
    }

    /**
     * The tenant of the lease, or {@code null} for a lease that has none.
     */
    public @Nullable Party getTenant() {
        return this.tenantId == null ? null : new Party.Personal(this.tenantId);
    }

    /**
     * The tenant of the lease, or {@code null} for a lease that has none.
     *
     * @deprecated use {@link #getTenant()}. Removed in 3.0.0.
     */
    @Deprecated(forRemoval = true)
    public @Nullable UUID getTenantId() {
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
