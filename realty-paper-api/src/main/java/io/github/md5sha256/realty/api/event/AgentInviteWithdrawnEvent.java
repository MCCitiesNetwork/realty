package io.github.md5sha256.realty.api.event;

import io.github.md5sha256.realty.api.WorldGuardRegion;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Called after an agent invite has been withdrawn.
 */
public class AgentInviteWithdrawnEvent extends RealtyRegionEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID inviterId;
    private final UUID inviteeId;

    public AgentInviteWithdrawnEvent(@NotNull WorldGuardRegion region,
                                     @NotNull UUID inviterId,
                                     @NotNull UUID inviteeId) {
        super(region);
        this.inviterId = inviterId;
        this.inviteeId = inviteeId;
    }

    /**
     * The player who withdrew the invite.
     */
    public @NotNull UUID getInviterId() {
        return this.inviterId;
    }

    /**
     * The player whose invite was withdrawn.
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
