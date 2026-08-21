package io.github.md5sha256.realty.api.event;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Fired at the landlord when a tenant rents their region.
 * Renders {@code notification.region-rented}.
 */
public final class RegionRentedEvent extends RealtyNotificationEvent {

    private final UUID tenantId;
    private final String tenantName;
    private final double price;
    private final long durationSeconds;

    public RegionRentedEvent(@NotNull UUID targetId,
                             @NotNull Component message,
                             @NotNull String regionId,
                             @NotNull UUID worldId,
                             @NotNull UUID tenantId,
                             @NotNull String tenantName,
                             double price,
                             long durationSeconds) {
        super(targetId, message, regionId, worldId);
        this.tenantId = tenantId;
        this.tenantName = tenantName;
        this.price = price;
        this.durationSeconds = durationSeconds;
    }

    public @NotNull UUID tenantId() {
        return this.tenantId;
    }

    public @NotNull String tenantName() {
        return this.tenantName;
    }

    public double price() {
        return this.price;
    }

    public long durationSeconds() {
        return this.durationSeconds;
    }
}
