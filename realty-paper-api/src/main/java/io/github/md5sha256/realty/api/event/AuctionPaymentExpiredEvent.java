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
                                      double refundAmount,
                                      boolean refundPaid,
                                      boolean oldRules) {
        super(regionId, worldId, region, refundAmount, refundPaid, oldRules);
        this.bidderId = bidderId;
    }

    /**
     * @deprecated says nothing of whether the refund was paid; use the constructor that does.
     */
    @Deprecated
    public AuctionPaymentExpiredEvent(@NotNull String regionId,
                                      @Nullable UUID worldId,
                                      @Nullable WorldGuardRegion region,
                                      @NotNull UUID bidderId,
                                      double refundAmount) {
        this(regionId, worldId, region, bidderId, refundAmount, false, false);
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
