package io.github.md5sha256.realty.api.event;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Fired at an offerer when their accepted offer's payment window expires unpaid.
 * Renders {@code notification.offer-payment-expired}.
 */
public final class OfferPaymentExpiredEvent extends RealtyNotificationEvent {

    private final UUID offererId;
    private final double refundAmount;

    public OfferPaymentExpiredEvent(@NotNull UUID targetId,
                                    @NotNull Component message,
                                    @NotNull String regionId,
                                    @NotNull UUID worldId,
                                    @NotNull UUID offererId,
                                    double refundAmount) {
        super(targetId, message, regionId, worldId);
        this.offererId = offererId;
        this.refundAmount = refundAmount;
    }

    public @NotNull UUID offererId() {
        return this.offererId;
    }

    public double refundAmount() {
        return this.refundAmount;
    }
}
