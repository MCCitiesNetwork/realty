package io.github.md5sha256.realty.api.event;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Fired at the offerer when the region's title holder accepts their offer.
 * Renders {@code notification.offer-accepted}.
 *
 * <p>The agreed price is deliberately not carried: the backing accept-offer result record holds no
 * such value, so there is nothing truthful to put here. Its absence is intentional, not an
 * oversight.</p>
 */
public final class OfferAcceptedEvent extends RealtyNotificationEvent {

    private final UUID offererId;

    public OfferAcceptedEvent(@NotNull UUID targetId,
                              @NotNull Component message,
                              @NotNull String regionId,
                              @NotNull UUID worldId,
                              @NotNull UUID offererId) {
        super(targetId, message, regionId, worldId);
        this.offererId = offererId;
    }

    public @NotNull UUID offererId() {
        return this.offererId;
    }
}
