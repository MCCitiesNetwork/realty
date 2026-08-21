package io.github.md5sha256.realty.api.event;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Fired at the agent when they are removed from a region.
 * Renders {@code notification.agent-removed}.
 */
public final class AgentRemovedEvent extends RealtyNotificationEvent {

    private final UUID removerId;
    private final String removerName;
    private final UUID agentId;

    public AgentRemovedEvent(@NotNull UUID targetId,
                             @NotNull Component message,
                             @NotNull String regionId,
                             @NotNull UUID worldId,
                             @NotNull UUID removerId,
                             @NotNull String removerName,
                             @NotNull UUID agentId) {
        super(targetId, message, regionId, worldId);
        this.removerId = removerId;
        this.removerName = removerName;
        this.agentId = agentId;
    }

    public @NotNull UUID removerId() {
        return this.removerId;
    }

    public @NotNull String removerName() {
        return this.removerName;
    }

    public @NotNull UUID agentId() {
        return this.agentId;
    }
}
