package io.github.md5sha256.realty.api.event;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Fired at the previous title holder when someone buys a region out from under them.
 * Renders {@code notification.region-bought}.
 */
public final class RegionBoughtEvent extends RealtyNotificationEvent {

    private final UUID buyerId;
    private final String buyerName;
    private final double price;

    public RegionBoughtEvent(@NotNull UUID targetId,
                             @NotNull Component message,
                             @NotNull String regionId,
                             @NotNull UUID worldId,
                             @NotNull UUID buyerId,
                             @NotNull String buyerName,
                             double price) {
        super(targetId, message, regionId, worldId);
        this.buyerId = buyerId;
        this.buyerName = buyerName;
        this.price = price;
    }

    public @NotNull UUID buyerId() {
        return this.buyerId;
    }

    public @NotNull String buyerName() {
        return this.buyerName;
    }

    public double price() {
        return this.price;
    }
}
