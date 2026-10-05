package io.github.md5sha256.realty.api.event;

import io.github.md5sha256.realty.api.Party;
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
    public @Nullable Party getNewTitleHolder() {
        return this.newTitleHolderId == null ? null : Party.personal(this.newTitleHolderId);
    }

    /**
     * The previous title holder, or {@code null} if there was none.
     */
    public @Nullable Party getPreviousTitleHolder() {
        return this.previousTitleHolderId == null ? null : Party.personal(this.previousTitleHolderId);
    }

    /**
     * The new title holder, or {@code null} if the title was cleared.
     *
     * @deprecated use {@link #getNewTitleHolder()}. Removed in 3.0.0.
     */
    @Deprecated(forRemoval = true)
    public @Nullable UUID getNewTitleHolderId() {
        return this.newTitleHolderId;
    }

    /**
     * The previous title holder, or {@code null} if there was none.
     *
     * @deprecated use {@link #getPreviousTitleHolder()}. Removed in 3.0.0.
     */
    @Deprecated(forRemoval = true)
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
