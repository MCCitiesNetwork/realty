package io.github.md5sha256.realty.api.event;

import io.github.md5sha256.realty.api.WorldGuardRegion;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * Called before a region is renamed, once the new name is known to be valid and free in
 * WorldGuard. The region still has its current name. The rename can still be refused afterwards,
 * for example when the actor does not manage the landlord; {@link RegionRenamedEvent} is what
 * says it happened.
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
     * The player renaming the region; empty when the console does.
     */
    public @NotNull Optional<UUID> getActorId() {
        return Optional.ofNullable(this.actorId);
    }

    /**
     * The name the region is to get. Region names are lower case, so this is the lower-cased form
     * of what the actor typed.
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
