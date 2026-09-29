package io.github.md5sha256.realty.api.event;

import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.api.WorldGuardRegion;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Called after the tenant of a region has been set.
 */
public class TenantSetEvent extends RealtyRegionEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID newTenantId;
    private final UUID previousTenantId;
    private final Party landlord;

    public TenantSetEvent(@NotNull WorldGuardRegion region,
                          @Nullable UUID newTenantId,
                          @Nullable UUID previousTenantId,
                          @NotNull Party landlord) {
        super(region);
        this.newTenantId = newTenantId;
        this.previousTenantId = previousTenantId;
        this.landlord = landlord;
    }

    /**
     * The new tenant, or {@code null} if the tenancy was cleared.
     */
    public @Nullable UUID getNewTenantId() {
        return this.newTenantId;
    }

    /**
     * The previous tenant, or {@code null} if there was none.
     */
    public @Nullable UUID getPreviousTenantId() {
        return this.previousTenantId;
    }

    /**
     * The landlord of the region.
     */
    public @NotNull Party getLandlord() {
        return this.landlord;
    }

    /**
     * The landlord of the region.
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
