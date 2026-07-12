package io.github.md5sha256.realty.api.event;

import io.github.md5sha256.realty.api.WorldGuardRegion;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Called before a player proposes a lease modification.
 *
 * <p>Cancelling this event prevents the proposal from being created.</p>
 */
public class LeaseModifyProposeEvent extends RealtyRegionEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID proposerId;
    private boolean cancelled;

    public LeaseModifyProposeEvent(@NotNull WorldGuardRegion region, @NotNull UUID proposerId) {
        super(region);
        this.proposerId = proposerId;
    }

    /**
     * The player proposing the lease modification.
     */
    public @NotNull UUID getProposerId() {
        return this.proposerId;
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
