package io.github.md5sha256.realty.api.event;

import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.api.WorldGuardRegion;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Called after a lease termination has been scheduled to take effect at a future date.
 */
public class LeaseTerminationScheduledEvent extends RealtyRegionEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Party landlord;
    private final UUID tenantId;
    private final String terminatedByRole;
    private final LocalDateTime effectiveDate;
    private final double charged;

    public LeaseTerminationScheduledEvent(@NotNull WorldGuardRegion region,
                                          @NotNull Party landlord,
                                          @NotNull UUID tenantId,
                                          @NotNull String terminatedByRole,
                                          @NotNull LocalDateTime effectiveDate,
                                          double charged) {
        super(region);
        this.landlord = landlord;
        this.tenantId = tenantId;
        this.terminatedByRole = terminatedByRole;
        this.effectiveDate = effectiveDate;
        this.charged = charged;
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
     * The tenant of the lease.
     */
    public @NotNull Party getTenant() {
        return Party.personal(this.tenantId);
    }

    /**
     * The tenant of the lease.
     *
     * @deprecated use {@link #getTenant()}. Removed in 3.0.0.
     */
    @Deprecated(forRemoval = true)
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
