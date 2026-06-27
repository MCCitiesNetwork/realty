package io.github.md5sha256.realty.api.event;

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
    private final UUID landlordId;

    public TenantSetEvent(@NotNull WorldGuardRegion region,
                          @Nullable UUID newTenantId,
                          @Nullable UUID previousTenantId,
                          @NotNull UUID landlordId) {
        super(region);
        this.newTenantId = newTenantId;
        this.previousTenantId = previousTenantId;
        this.landlordId = landlordId;
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
