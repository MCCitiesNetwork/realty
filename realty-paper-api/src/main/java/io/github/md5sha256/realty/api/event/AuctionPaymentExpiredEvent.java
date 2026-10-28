package io.github.md5sha256.realty.api.event;

import io.github.md5sha256.realty.api.WorldGuardRegion;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Called after a player who won an auction missed the payment deadline.
 */
public class AuctionPaymentExpiredEvent extends PaymentExpiredEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID bidderId;

    public AuctionPaymentExpiredEvent(@NotNull String regionId,
                                      @Nullable UUID worldId,
                                      @Nullable WorldGuardRegion region,
                                      @NotNull UUID bidderId,
                                      double refundAmount) {
        super(regionId, worldId, region, refundAmount);
        this.bidderId = bidderId;
    }

    /**
     * The player who missed the deadline.
     */
    public @NotNull UUID getBidderId() {
        return this.bidderId;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
