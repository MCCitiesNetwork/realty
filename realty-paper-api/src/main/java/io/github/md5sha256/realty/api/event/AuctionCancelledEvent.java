package io.github.md5sha256.realty.api.event;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Fired at the auctioneer when an auction on their region is cancelled.
 * Renders {@code notification.auction-cancelled}.
 */
public final class AuctionCancelledEvent extends RealtyNotificationEvent {

    private final UUID cancelledById;

    public AuctionCancelledEvent(@NotNull UUID targetId,
                                 @NotNull Component message,
                                 @NotNull String regionId,
                                 @NotNull UUID worldId,
                                 @Nullable UUID cancelledById) {
        super(targetId, message, regionId, worldId);
        this.cancelledById = cancelledById;
    }

    /**
     * The player who ran the cancel command, or {@code null} when it was run from
     * console or a command block.
     */
    public @Nullable UUID cancelledById() {
        return this.cancelledById;
    }
}
