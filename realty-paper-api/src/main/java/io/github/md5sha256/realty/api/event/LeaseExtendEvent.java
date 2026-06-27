package io.github.md5sha256.realty.api.event;

import io.github.md5sha256.realty.api.WorldGuardRegion;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Called before a tenant extends (renews) their lease.
 *
 * <p>Cancelling this event prevents the extension entirely: no economy transfer
 * or database change occurs.</p>
 */
public class LeaseExtendEvent extends RealtyRegionEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID tenantId;
    private boolean cancelled;

    public LeaseExtendEvent(@NotNull WorldGuardRegion region, @NotNull UUID tenantId) {
        super(region);
        this.tenantId = tenantId;
    }

    /**
     * The tenant attempting to extend the lease.
     */
    public @NotNull UUID getTenantId() {
        return this.tenantId;
    }

    @Override
    public boolean isCancelled() {
        return this.cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
