package io.github.md5sha256.realty.database.entity;

import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Internal entity record mapping to the {@code FreeholdContractAgentInvite} DDL table.
 *
 * @param realtyRegionId FK to the RealtyRegion table
 * @param inviterId      UUID of the player who sent the invite
 * @param inviteeId      UUID of the invited player
 * @param inviteTime     when the invite was created
 */
public record FreeholdContractAgentInviteEntity(
        int realtyRegionId,
        @NotNull UUID inviterId,
        @NotNull UUID inviteeId,
        @NotNull LocalDateTime inviteTime
) {
}
