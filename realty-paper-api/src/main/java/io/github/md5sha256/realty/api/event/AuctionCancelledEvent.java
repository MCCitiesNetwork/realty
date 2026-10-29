package io.github.md5sha256.realty.api.event;

import io.github.md5sha256.realty.api.WorldGuardRegion;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Called after an auction on a region has been cancelled.
 */
public class AuctionCancelledEvent extends RealtyRegionEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID actorId;
    private final List<UUID> bidderIds;

    public AuctionCancelledEvent(@NotNull WorldGuardRegion region,
                                 @Nullable UUID actorId,
                                 @NotNull List<UUID> bidderIds) {
        super(region);
        this.actorId = actorId;
        this.bidderIds = List.copyOf(bidderIds);
    }

    /**
     * The player who cancelled the auction, or empty when the console or a plugin did.
     */
    public @NotNull Optional<UUID> getActorId() {
        return Optional.ofNullable(this.actorId);
    }

    /**
     * Everyone who had bid on the auction.
     */
    public @NotNull List<UUID> getBidderIds() {
        return this.bidderIds;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
