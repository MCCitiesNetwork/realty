package io.github.md5sha256.realty.api.event;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Fired at the tenant when their leasehold contract expires.
 * Renders {@code notification.leasehold-expired}.
 */
public final class LeaseholdExpiredEvent extends RealtyNotificationEvent {

    private final UUID tenantId;
    private final UUID landlordId;

    public LeaseholdExpiredEvent(@NotNull UUID targetId,
                                 @NotNull Component message,
                                 @NotNull String regionId,
                                 @NotNull UUID worldId,
                                 @NotNull UUID tenantId,
                                 @NotNull UUID landlordId) {
        super(targetId, message, regionId, worldId);
        this.tenantId = tenantId;
        this.landlordId = landlordId;
    }

    public @NotNull UUID tenantId() {
        return this.tenantId;
    }

    public @NotNull UUID landlordId() {
        return this.landlordId;
    }
}
