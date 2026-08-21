package io.github.md5sha256.realty.api.event;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Fired at the landlord when a tenant ends their rental early.
 * Renders {@code notification.region-unrented}.
 */
public final class RegionUnrentedEvent extends RealtyNotificationEvent {

    private final UUID tenantId;
    private final String tenantName;
    private final double refund;

    public RegionUnrentedEvent(@NotNull UUID targetId,
                               @NotNull Component message,
                               @NotNull String regionId,
                               @NotNull UUID worldId,
                               @NotNull UUID tenantId,
                               @NotNull String tenantName,
                               double refund) {
        super(targetId, message, regionId, worldId);
        this.tenantId = tenantId;
        this.tenantName = tenantName;
        this.refund = refund;
    }

    public @NotNull UUID tenantId() {
        return this.tenantId;
    }

    public @NotNull String tenantName() {
        return this.tenantName;
    }

    public double refund() {
        return this.refund;
    }
}
