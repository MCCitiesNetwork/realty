package io.github.md5sha256.realty.api.event;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Fired at a bidder when their winning auction bid payment window expires unpaid.
 * Renders {@code notification.bid-payment-expired}.
 */
public final class BidPaymentExpiredEvent extends RealtyNotificationEvent {

    private final UUID bidderId;
    private final double refundAmount;

    public BidPaymentExpiredEvent(@NotNull UUID targetId,
                                  @NotNull Component message,
                                  @NotNull String regionId,
                                  @NotNull UUID worldId,
                                  @NotNull UUID bidderId,
                                  double refundAmount) {
        super(targetId, message, regionId, worldId);
        this.bidderId = bidderId;
        this.refundAmount = refundAmount;
    }

    public @NotNull UUID bidderId() {
        return this.bidderId;
    }

    public double refundAmount() {
        return this.refundAmount;
    }
}
