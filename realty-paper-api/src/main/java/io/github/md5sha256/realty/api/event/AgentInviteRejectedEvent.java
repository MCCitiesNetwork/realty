package io.github.md5sha256.realty.api.event;

import io.github.md5sha256.realty.api.WorldGuardRegion;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Called after an agent invite has been rejected.
 */
public class AgentInviteRejectedEvent extends RealtyRegionEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID inviteeId;

    public AgentInviteRejectedEvent(@NotNull WorldGuardRegion region,
                                    @NotNull UUID inviteeId) {
        super(region);
        this.inviteeId = inviteeId;
    }

    /**
     * The player who rejected the invite.
     */
    public @NotNull UUID getInviteeId() {
        return this.inviteeId;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
