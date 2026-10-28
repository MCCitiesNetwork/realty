package io.github.md5sha256.realty.api.event;

import io.github.md5sha256.realty.api.WorldGuardRegion;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Called after a bid has been placed on an auctioned region.
 */
public class AuctionBidPlacedEvent extends RealtyRegionEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID bidderId;
    private final double bidAmount;
    private final UUID previousBidderId;
    private final Instant biddingEndsAt;

    public AuctionBidPlacedEvent(@NotNull WorldGuardRegion region,
                                 @NotNull UUID bidderId,
                                 double bidAmount,
                                 @Nullable UUID previousBidderId,
                                 @NotNull Instant biddingEndsAt) {
        super(region);
        this.bidderId = bidderId;
        this.bidAmount = bidAmount;
        this.previousBidderId = previousBidderId;
        this.biddingEndsAt = biddingEndsAt;
    }

    /**
     * The player who placed the bid.
     */
    public @NotNull UUID getBidderId() {
        return this.bidderId;
    }

    /**
     * The amount bid.
     */
    public double getBidAmount() {
        return this.bidAmount;
    }

    /**
     * The player who held the highest bid before this one, or empty if this is the first bid.
     */
    public @NotNull Optional<UUID> getPreviousBidderId() {
        return Optional.ofNullable(this.previousBidderId);
    }

    /**
     * When bidding now ends, unless another bid moves it later.
     */
    public @NotNull Instant getBiddingEndsAt() {
        return this.biddingEndsAt;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
