package io.github.md5sha256.realty.api.event;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Fired at the region's title holder when an offerer withdraws their offer.
 * Renders {@code notification.offer-withdrawn}.
 */
public final class OfferWithdrawnEvent extends RealtyNotificationEvent {

    private final UUID offererId;
    private final String offererName;

    public OfferWithdrawnEvent(@NotNull UUID targetId,
                               @NotNull Component message,
                               @NotNull String regionId,
                               @NotNull UUID worldId,
                               @NotNull UUID offererId,
                               @NotNull String offererName) {
        super(targetId, message, regionId, worldId);
        this.offererId = offererId;
        this.offererName = offererName;
    }

    public @NotNull UUID offererId() {
        return this.offererId;
    }

    public @NotNull String offererName() {
        return this.offererName;
    }
}
