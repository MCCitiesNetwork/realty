package io.github.md5sha256.realty.api.event;

import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import io.github.md5sha256.realty.api.WorldGuardRegion;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * Called before a region's shape is changed, once the actor may redefine it and the new shape
 * passes Realty's checks. The region still has its current shape. A redefine that this event lets
 * through goes ahead; {@link RegionRedefinedEvent} is what says it happened.
 */
public class RegionRedefineEvent extends RealtyRegionEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID actorId;
    private final ProtectedRegion newRegion;
    private boolean cancelled;

    public RegionRedefineEvent(@NotNull WorldGuardRegion region, @Nullable UUID actorId,
                               @NotNull ProtectedRegion newRegion) {
        super(region);
        this.actorId = actorId;
        this.newRegion = newRegion;
    }

    /**
     * The player redefining the region; empty when the console does.
     */
    public @NotNull Optional<UUID> getActorId() {
        return Optional.ofNullable(this.actorId);
    }

    /**
     * The region that is to replace it: the same id and the new shape. It is not yet in WorldGuard.
     */
    public @NotNull ProtectedRegion getNewRegion() {
        return this.newRegion;
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
