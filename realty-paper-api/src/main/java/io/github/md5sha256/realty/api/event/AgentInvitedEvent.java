package io.github.md5sha256.realty.api.event;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Fired at the invitee when someone invites them to become an agent on a region.
 * Renders {@code notification.agent-invited}.
 */
public final class AgentInvitedEvent extends RealtyNotificationEvent {

    private final UUID inviterId;
    private final String inviterName;
    private final UUID inviteeId;

    public AgentInvitedEvent(@NotNull UUID targetId,
                             @NotNull Component message,
                             @NotNull String regionId,
                             @NotNull UUID worldId,
                             @NotNull UUID inviterId,
                             @NotNull String inviterName,
                             @NotNull UUID inviteeId) {
        super(targetId, message, regionId, worldId);
        this.inviterId = inviterId;
        this.inviterName = inviterName;
        this.inviteeId = inviteeId;
    }

    public @NotNull UUID inviterId() {
        return this.inviterId;
    }

    public @NotNull String inviterName() {
        return this.inviterName;
    }

    public @NotNull UUID inviteeId() {
        return this.inviteeId;
    }
}
