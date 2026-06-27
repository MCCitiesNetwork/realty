package io.github.md5sha256.realty.api.event;

import io.github.md5sha256.realty.api.WorldGuardRegion;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Called before an agent is invited to a region.
 */
public class AgentInviteEvent extends RealtyRegionEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID inviterId;
    private final UUID inviteeId;
    private boolean cancelled;

    public AgentInviteEvent(@NotNull WorldGuardRegion region,
                            @NotNull UUID inviterId,
                            @NotNull UUID inviteeId) {
        super(region);
        this.inviterId = inviterId;
        this.inviteeId = inviteeId;
    }

    /**
     * The player who issued the invite.
     */
    public @NotNull UUID getInviterId() {
        return this.inviterId;
    }

    /**
     * The player who was invited.
     */
    public @NotNull UUID getInviteeId() {
        return this.inviteeId;
    }

    @Override
    public boolean isCancelled() {
        return this.cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
