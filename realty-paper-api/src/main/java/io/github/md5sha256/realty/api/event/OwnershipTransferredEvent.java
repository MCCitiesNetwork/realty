package io.github.md5sha256.realty.api.event;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Fired at the previous title holder when ownership of a region is transferred to someone else.
 * Renders {@code notification.ownership-transferred}.
 */
public final class OwnershipTransferredEvent extends RealtyNotificationEvent {

    private final UUID newHolderId;
    private final String newHolderName;

    public OwnershipTransferredEvent(@NotNull UUID targetId,
                                     @NotNull Component message,
                                     @NotNull String regionId,
                                     @NotNull UUID worldId,
                                     @NotNull UUID newHolderId,
                                     @NotNull String newHolderName) {
        super(targetId, message, regionId, worldId);
        this.newHolderId = newHolderId;
        this.newHolderName = newHolderName;
    }

    public @NotNull UUID newHolderId() {
        return this.newHolderId;
    }

    public @NotNull String newHolderName() {
        return this.newHolderName;
    }
}
