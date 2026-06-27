package io.github.md5sha256.realty.api.event;

import io.github.md5sha256.realty.api.WorldGuardRegion;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Called before a region is deleted.
 */
public class RegionDeleteEvent extends RealtyRegionEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID actorId;
    private boolean cancelled;

    public RegionDeleteEvent(@NotNull WorldGuardRegion region, @NotNull UUID actorId) {
        super(region);
        this.actorId = actorId;
    }

    /**
     * The player deleting the region.
     */
    public @NotNull UUID getActorId() {
        return this.actorId;
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
