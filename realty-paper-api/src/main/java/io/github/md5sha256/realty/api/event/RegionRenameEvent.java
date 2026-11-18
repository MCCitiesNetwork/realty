package io.github.md5sha256.realty.api.event;

import io.github.md5sha256.realty.api.WorldGuardRegion;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Called before a region is renamed. The region still has its current name.
 */
public class RegionRenameEvent extends RealtyRegionEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID actorId;
    private final String newName;
    private boolean cancelled;

    public RegionRenameEvent(@NotNull WorldGuardRegion region, @Nullable UUID actorId, @NotNull String newName) {
        super(region);
        this.actorId = actorId;
        this.newName = newName;
    }

    /**
     * The player renaming the region, or {@code null} when the console does.
     */
    public @Nullable UUID getActorId() {
        return this.actorId;
    }

    /**
     * The name the region is to get, as the actor typed it.
     */
    public @NotNull String getNewName() {
        return this.newName;
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
