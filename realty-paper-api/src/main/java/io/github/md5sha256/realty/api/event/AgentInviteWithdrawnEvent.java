package io.github.md5sha256.realty.api.event;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Fired at the invitee when the inviter withdraws their agent invitation.
 * Renders {@code notification.agent-invite-withdrawn}.
 */
public final class AgentInviteWithdrawnEvent extends RealtyNotificationEvent {

    private final UUID inviterId;
    private final String inviterName;
    private final UUID inviteeId;

    public AgentInviteWithdrawnEvent(@NotNull UUID targetId,
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
