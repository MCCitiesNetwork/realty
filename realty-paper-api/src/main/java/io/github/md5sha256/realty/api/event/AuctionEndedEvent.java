package io.github.md5sha256.realty.api.event;

import io.github.md5sha256.realty.api.WorldGuardRegion;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Called after an auction has ended, whether or not a winning bid was placed.
 */
public class AuctionEndedEvent extends RealtyRegionEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID winnerId;
    private final UUID auctioneerId;

    public AuctionEndedEvent(@NotNull WorldGuardRegion region,
                             @Nullable UUID winnerId,
                             @NotNull UUID auctioneerId) {
        super(region);
        this.winnerId = winnerId;
        this.auctioneerId = auctioneerId;
    }

    /**
     * The winning bidder, or {@code null} if the auction ended without a winning
     * bid.
     */
    public @Nullable UUID getWinnerId() {
        return this.winnerId;
    }

    /**
     * The player who created the auction.
     */
    public @NotNull UUID getAuctioneerId() {
        return this.auctioneerId;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
