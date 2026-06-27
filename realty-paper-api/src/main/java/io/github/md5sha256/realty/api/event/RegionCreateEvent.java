package io.github.md5sha256.realty.api.event;

import io.github.md5sha256.realty.api.WorldGuardRegion;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Called before a region is created.
 */
public class RegionCreateEvent extends RealtyRegionEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID creatorId;
    private boolean cancelled;

    public RegionCreateEvent(@NotNull WorldGuardRegion region, @NotNull UUID creatorId) {
        super(region);
        this.creatorId = creatorId;
    }

    /**
     * The player creating the region.
     */
    public @NotNull UUID getCreatorId() {
        return this.creatorId;
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
