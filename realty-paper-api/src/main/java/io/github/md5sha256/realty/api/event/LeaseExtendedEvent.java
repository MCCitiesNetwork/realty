package io.github.md5sha256.realty.api.event;

import io.github.md5sha256.realty.api.WorldGuardRegion;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Called after a tenant has successfully extended (renewed) their lease. The
 * economy transfer and lease extension have already been committed.
 */
public class LeaseExtendedEvent extends RealtyRegionEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID tenantId;
    private final double price;

    public LeaseExtendedEvent(@NotNull WorldGuardRegion region,
                              @NotNull UUID tenantId,
                              double price) {
        super(region);
        this.tenantId = tenantId;
        this.price = price;
    }

    /**
     * The tenant whose lease was extended.
     */
    public @NotNull UUID getTenantId() {
        return this.tenantId;
    }

    /**
     * The price paid for the extension.
     */
    public double getPrice() {
        return this.price;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
