package io.github.md5sha256.realty.api.event;

import io.github.md5sha256.realty.api.WorldGuardRegion;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Called after a player who had an offer accepted missed the payment deadline.
 */
public class OfferPaymentExpiredEvent extends PaymentExpiredEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID offererId;

    public OfferPaymentExpiredEvent(@NotNull String regionId,
                                    @Nullable UUID worldId,
                                    @Nullable WorldGuardRegion region,
                                    @NotNull UUID offererId,
                                    double refundAmount) {
        super(regionId, worldId, region, refundAmount);
        this.offererId = offererId;
    }

    /**
     * The player who missed the deadline.
     */
    public @NotNull UUID getOffererId() {
        return this.offererId;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
