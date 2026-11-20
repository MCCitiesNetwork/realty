package io.github.md5sha256.realty.api.event;

import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import io.github.md5sha256.realty.api.WorldGuardRegion;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * Called after a region's shape has changed. The region has its new shape; its id, owners,
 * members, flags, priority, parent and children are as they were.
 */
public class RegionRedefinedEvent extends RealtyRegionEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID actorId;
    private final ProtectedRegion oldRegion;

    public RegionRedefinedEvent(@NotNull WorldGuardRegion region, @Nullable UUID actorId,
                                @NotNull ProtectedRegion oldRegion) {
        super(region);
        this.actorId = actorId;
        this.oldRegion = oldRegion;
    }

    /**
     * The player who redefined the region; empty when the console did.
     */
    public @NotNull Optional<UUID> getActorId() {
        return Optional.ofNullable(this.actorId);
    }

    /**
     * The region it replaced, with the old shape. It is no longer in WorldGuard.
     */
    public @NotNull ProtectedRegion getOldRegion() {
        return this.oldRegion;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
