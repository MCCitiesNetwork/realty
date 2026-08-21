package io.github.md5sha256.realty.api.event;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Fired at the inviter when the invitee rejects their agent invitation.
 * Renders {@code notification.agent-invite-rejected}.
 */
public final class AgentInviteRejectedEvent extends RealtyNotificationEvent {

    private final UUID inviterId;
    private final UUID inviteeId;
    private final String inviteeName;

    public AgentInviteRejectedEvent(@NotNull UUID targetId,
                                    @NotNull Component message,
                                    @NotNull String regionId,
                                    @NotNull UUID worldId,
                                    @NotNull UUID inviterId,
                                    @NotNull UUID inviteeId,
                                    @NotNull String inviteeName) {
        super(targetId, message, regionId, worldId);
        this.inviterId = inviterId;
        this.inviteeId = inviteeId;
        this.inviteeName = inviteeName;
    }

    public @NotNull UUID inviterId() {
        return this.inviterId;
    }

    public @NotNull UUID inviteeId() {
        return this.inviteeId;
    }

    public @NotNull String inviteeName() {
        return this.inviteeName;
    }
}
