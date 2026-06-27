package io.github.md5sha256.realty.api.event;

import io.github.md5sha256.realty.api.WorldGuardRegion;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Called after the title of a region has been transferred to a new holder.
 */
public class TitleTransferredEvent extends RealtyRegionEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID newTitleHolderId;
    private final UUID previousTitleHolderId;

    public TitleTransferredEvent(@NotNull WorldGuardRegion region,
                                 @Nullable UUID newTitleHolderId,
                                 @Nullable UUID previousTitleHolderId) {
        super(region);
        this.newTitleHolderId = newTitleHolderId;
        this.previousTitleHolderId = previousTitleHolderId;
    }

    /**
     * The new title holder, or {@code null} if the title was cleared.
     */
    public @Nullable UUID getNewTitleHolderId() {
        return this.newTitleHolderId;
    }

    /**
     * The previous title holder, or {@code null} if there was none.
     */
    public @Nullable UUID getPreviousTitleHolderId() {
        return this.previousTitleHolderId;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
