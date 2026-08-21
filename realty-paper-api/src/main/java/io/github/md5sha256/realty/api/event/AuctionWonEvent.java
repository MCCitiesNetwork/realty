package io.github.md5sha256.realty.api.event;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Fired at the auctioneer when an auction on their region concludes with a winning bidder.
 * Renders {@code notification.auction-won}.
 */
public final class AuctionWonEvent extends RealtyNotificationEvent {

    private final UUID winnerId;

    public AuctionWonEvent(@NotNull UUID targetId,
                           @NotNull Component message,
                           @NotNull String regionId,
                           @NotNull UUID worldId,
                           @NotNull UUID winnerId) {
        super(targetId, message, regionId, worldId);
        this.winnerId = winnerId;
    }

    public @NotNull UUID winnerId() {
        return this.winnerId;
    }
}
