package io.github.md5sha256.realty.api.event;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Fired at the previous highest bidder when someone outbids them in an auction.
 * Renders {@code notification.outbid}.
 */
public final class OutbidEvent extends RealtyNotificationEvent {

    private final UUID newBidderId;
    private final double newBidAmount;

    public OutbidEvent(@NotNull UUID targetId,
                       @NotNull Component message,
                       @NotNull String regionId,
                       @NotNull UUID worldId,
                       @NotNull UUID newBidderId,
                       double newBidAmount) {
        super(targetId, message, regionId, worldId);
        this.newBidderId = newBidderId;
        this.newBidAmount = newBidAmount;
    }

    public @NotNull UUID newBidderId() {
        return this.newBidderId;
    }

    public double newBidAmount() {
        return this.newBidAmount;
    }
}
