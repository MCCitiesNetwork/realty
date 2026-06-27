package io.github.md5sha256.realty.api.event;

import io.github.md5sha256.realty.api.WorldGuardRegion;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Called after a region has been deleted.
 */
public class RegionDeletedEvent extends RealtyRegionEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID actorId;

    public RegionDeletedEvent(@NotNull WorldGuardRegion region, @NotNull UUID actorId) {
        super(region);
        this.actorId = actorId;
    }

    /**
     * The player who deleted the region.
     */
    public @NotNull UUID getActorId() {
        return this.actorId;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
