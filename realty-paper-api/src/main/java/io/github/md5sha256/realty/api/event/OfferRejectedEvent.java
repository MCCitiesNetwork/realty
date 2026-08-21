package io.github.md5sha256.realty.api.event;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Fired at every offerer whose offer on a region was rejected — one offerer for
 * {@code /realty offer reject}, all of them for {@code /realty offer rejectall}.
 * Renders {@code notification.offer-rejected}.
 */
public final class OfferRejectedEvent extends RealtyNotificationEvent {

    public OfferRejectedEvent(@NotNull List<UUID> targetIds,
                              @NotNull Component message,
                              @NotNull String regionId,
                              @NotNull UUID worldId) {
        super(targetIds, message, regionId, worldId);
    }
}
