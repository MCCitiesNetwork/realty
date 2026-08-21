package io.github.md5sha256.realty.api.event;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Fired at the region's title holder when someone places an offer on their region.
 * Renders {@code notification.offer-placed}.
 */
public final class OfferPlacedEvent extends RealtyNotificationEvent {

    private final UUID offererId;
    private final String offererName;
    private final double price;

    public OfferPlacedEvent(@NotNull UUID targetId,
                            @NotNull Component message,
                            @NotNull String regionId,
                            @NotNull UUID worldId,
                            @NotNull UUID offererId,
                            @NotNull String offererName,
                            double price) {
        super(targetId, message, regionId, worldId);
        this.offererId = offererId;
        this.offererName = offererName;
        this.price = price;
    }

    public @NotNull UUID offererId() {
        return this.offererId;
    }

    public @NotNull String offererName() {
        return this.offererName;
    }

    public double price() {
        return this.price;
    }
}
