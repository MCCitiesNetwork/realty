package io.github.md5sha256.realty.api.event;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Fired at the auctioneer when an auction on their region is cancelled.
 * Renders {@code notification.auction-cancelled}.
 */
public final class AuctionCancelledEvent extends RealtyNotificationEvent {

    private final UUID auctioneerId;

    public AuctionCancelledEvent(@NotNull UUID targetId,
                                 @NotNull Component message,
                                 @NotNull String regionId,
                                 @NotNull UUID worldId,
                                 @NotNull UUID auctioneerId) {
        super(targetId, message, regionId, worldId);
        this.auctioneerId = auctioneerId;
    }

    public @NotNull UUID auctioneerId() {
        return this.auctioneerId;
    }
}
