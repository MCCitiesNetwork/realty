package io.github.md5sha256.realty.api.event;

import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.api.WorldGuardRegion;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    private final Party landlord;
    private final @Nullable UUID tenantId;

    public LeaseModificationResolvedEvent(@NotNull WorldGuardRegion region,
                                          @NotNull String resolution,
                                          @NotNull String proposerRole,
                                          @NotNull Party landlord,
                                          @Nullable UUID tenantId) {
        super(region);
        this.resolution = resolution;
        this.proposerRole = proposerRole;
        this.landlord = landlord;
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
     * The tenant of the lease, or {@code null} when the lease has no tenant, for instance when a
     * landlord's proposal is withdrawn after the tenant left.
     */
    public @Nullable Party getTenant() {
        return this.tenantId == null ? null : new Party.Personal(this.tenantId);
    }

    /**
     * The tenant of the lease, or {@code null} when the lease has no tenant.
     *
     * @deprecated use {@link #getTenant()}. Removed in 3.0.0.
     */
    @Deprecated(forRemoval = true)
    public @Nullable UUID getTenantId() {
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
