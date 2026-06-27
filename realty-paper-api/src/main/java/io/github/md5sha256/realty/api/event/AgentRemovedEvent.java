package io.github.md5sha256.realty.api.event;

import io.github.md5sha256.realty.api.WorldGuardRegion;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Called after an agent has been removed from a region.
 */
public class AgentRemovedEvent extends RealtyRegionEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID actorId;
    private final UUID agentId;

    public AgentRemovedEvent(@NotNull WorldGuardRegion region,
                             @NotNull UUID actorId,
                             @NotNull UUID agentId) {
        super(region);
        this.actorId = actorId;
        this.agentId = agentId;
    }

    /**
     * The player who removed the agent.
     */
    public @NotNull UUID getActorId() {
        return this.actorId;
    }

    /**
     * The agent who was removed.
     */
    public @NotNull UUID getAgentId() {
        return this.agentId;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
