package io.github.md5sha256.realty.api.event;

import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.api.WorldGuardRegion;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Called after a lease has expired and the tenant has been removed from the
 * region. Fired by the periodic expiry task, so there is no acting player and
 * the event is not cancellable.
 */
public class LeaseExpiredEvent extends RealtyRegionEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID tenantId;
    private final Party landlord;

    public LeaseExpiredEvent(@NotNull WorldGuardRegion region,
                             @NotNull UUID tenantId,
                             @NotNull Party landlord) {
        super(region);
        this.tenantId = tenantId;
        this.landlord = landlord;
    }

    /**
     * The former tenant whose lease expired.
     */
    public @NotNull Party getTenant() {
        return new Party.Personal(this.tenantId);
    }

    /**
     * The former tenant whose lease expired.
     *
     * @deprecated use {@link #getTenant()}. Removed in 3.0.0.
     */
    @Deprecated(forRemoval = true)
    public @NotNull UUID getTenantId() {
        return this.tenantId;
    }

    /**
     * The landlord of the expired lease.
     */
    public @NotNull Party getLandlord() {
        return this.landlord;
    }

    /**
     * The landlord of the expired lease.
     *
     * @deprecated use {@link #getLandlord()}; {@code null} when the landlord is not a player.
     * Removed in 3.0.0.
     */
    @Deprecated(forRemoval = true)
    public @Nullable UUID getLandlordId() {
        return Party.playerUuidOf(this.landlord).orElse(null);
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
