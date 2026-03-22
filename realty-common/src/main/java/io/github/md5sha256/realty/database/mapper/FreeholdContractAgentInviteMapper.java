package io.github.md5sha256.realty.database.mapper;

import io.github.md5sha256.realty.database.entity.FreeholdContractAgentInviteEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Base mapper interface for CRUD operations on the {@code FreeholdContractAgentInvite} table.
 * SQL annotations are provided by database-specific sub-interfaces.
 *
 * @see FreeholdContractAgentInviteEntity
 */
public interface FreeholdContractAgentInviteMapper {

    boolean existsByRegionAndInvitee(@NotNull String worldGuardRegionId,
                                     @NotNull UUID worldId,
                                     @NotNull UUID inviteeId);

    @Nullable FreeholdContractAgentInviteEntity selectByRegionAndInvitee(@NotNull String worldGuardRegionId,
                                                                         @NotNull UUID worldId,
                                                                         @NotNull UUID inviteeId);

    int insert(@NotNull String worldGuardRegionId,
               @NotNull UUID worldId,
               @NotNull UUID inviterId,
               @NotNull UUID inviteeId);

    int deleteByRegionAndInvitee(@NotNull String worldGuardRegionId,
                                  @NotNull UUID worldId,
                                  @NotNull UUID inviteeId);
}
