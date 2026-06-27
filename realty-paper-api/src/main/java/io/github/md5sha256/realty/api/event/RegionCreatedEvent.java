package io.github.md5sha256.realty.api.event;

import io.github.md5sha256.realty.api.WorldGuardRegion;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Called after a region has been created.
 */
public class RegionCreatedEvent extends RealtyRegionEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID creatorId;

    public RegionCreatedEvent(@NotNull WorldGuardRegion region, @NotNull UUID creatorId) {
        super(region);
        this.creatorId = creatorId;
    }

    /**
     * The player who created the region.
     */
    public @NotNull UUID getCreatorId() {
        return this.creatorId;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
