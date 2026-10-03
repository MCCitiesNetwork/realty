package io.github.md5sha256.realty.api.event;

import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.api.WorldGuardRegion;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Called after a tenant has successfully ended a lease early. The refund and
 * tenancy change have already been committed.
 */
public class RegionUnrentedEvent extends RealtyRegionEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID tenantId;
    private final Party landlord;
    private final double refund;

    public RegionUnrentedEvent(@NotNull WorldGuardRegion region,
                               @NotNull UUID tenantId,
                               @NotNull Party landlord,
                               double refund) {
        super(region);
        this.tenantId = tenantId;
        this.landlord = landlord;
        this.refund = refund;
    }

    /**
     * The former tenant.
     */
    public @NotNull Party getTenant() {
        return new Party.Personal(this.tenantId);
    }

    /**
     * The former tenant.
     *
     * @deprecated use {@link #getTenant()}. Removed in 3.0.0.
     */
    @Deprecated(forRemoval = true)
    public @NotNull UUID getTenantId() {
        return this.tenantId;
    }

    /**
     * The landlord who paid the refund.
     */
    public @NotNull Party getLandlord() {
        return this.landlord;
    }

    /**
     * The landlord who paid the refund.
     *
     * @deprecated use {@link #getLandlord()}; {@code null} when the landlord is not a player.
     * Removed in 3.0.0.
     */
    @Deprecated(forRemoval = true)
    public @Nullable UUID getLandlordId() {
        return Party.playerUuidOf(this.landlord).orElse(null);
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
