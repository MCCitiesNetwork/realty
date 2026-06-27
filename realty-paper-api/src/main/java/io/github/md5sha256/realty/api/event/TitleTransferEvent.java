package io.github.md5sha256.realty.api.event;

import io.github.md5sha256.realty.api.WorldGuardRegion;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Called before the title of a region is transferred to a new holder.
 */
public class TitleTransferEvent extends RealtyRegionEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID newTitleHolderId;
    private boolean cancelled;

    public TitleTransferEvent(@NotNull WorldGuardRegion region, @Nullable UUID newTitleHolderId) {
        super(region);
        this.newTitleHolderId = newTitleHolderId;
    }

    /**
     * The new title holder, or {@code null} if the title is being cleared.
     */
    public @Nullable UUID getNewTitleHolderId() {
        return this.newTitleHolderId;
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
