package io.github.md5sha256.realty.api.event;

import io.github.md5sha256.realty.api.WorldGuardRegion;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Called after a region has been renamed. {@link #getRegion()} is the region under its new name;
 * the WorldGuard region with the old name no longer exists. A plugin that stores region names
 * should move its records from {@link #getOldName()} to the new name here.
 */
public class RegionRenamedEvent extends RealtyRegionEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID actorId;
    private final String oldName;

    public RegionRenamedEvent(@NotNull WorldGuardRegion region, @Nullable UUID actorId, @NotNull String oldName) {
        super(region);
        this.actorId = actorId;
        this.oldName = oldName;
    }

    /**
     * The player who renamed the region, or {@code null} when the console did.
     */
    public @Nullable UUID getActorId() {
        return this.actorId;
    }

    /**
     * The name the region had before.
     */
    public @NotNull String getOldName() {
        return this.oldName;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
