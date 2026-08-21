package io.github.md5sha256.realty.api.event;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Fired at the winning bidder when an auction they were leading concludes.
 * Renders {@code notification.auction-won}.
 *
 * <p>The winning bid amount is deliberately not carried: the backing
 * {@code RealtyBackend.ExpiredBiddingAuction} record holds no such value, so there is nothing
 * truthful to put here. Its absence is intentional, not an oversight.</p>
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
