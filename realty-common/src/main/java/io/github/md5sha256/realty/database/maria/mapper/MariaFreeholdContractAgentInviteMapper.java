package io.github.md5sha256.realty.database.maria.mapper;

import io.github.md5sha256.realty.database.entity.FreeholdContractAgentInviteEntity;
import io.github.md5sha256.realty.database.mapper.FreeholdContractAgentInviteMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * MariaDB-specific MyBatis mapper for the {@code FreeholdContractAgentInvite} table.
 *
 * @see FreeholdContractAgentInviteEntity
 */
public interface MariaFreeholdContractAgentInviteMapper extends FreeholdContractAgentInviteMapper {

    @Override
    @Select("""
            SELECT COUNT(*) > 0
            FROM FreeholdContractAgentInvite ai
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = ai.realtyRegionId
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            AND ai.inviteeId = #{inviteeId}
            """)
    boolean existsByRegionAndInvitee(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                                     @Param("worldId") @NotNull UUID worldId,
                                     @Param("inviteeId") @NotNull UUID inviteeId);

    @Override
    @Select("""
            SELECT ai.realtyRegionId, ai.inviterId, ai.inviteeId, ai.inviteTime
            FROM FreeholdContractAgentInvite ai
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = ai.realtyRegionId
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            AND ai.inviteeId = #{inviteeId}
            """)
    @Nullable FreeholdContractAgentInviteEntity selectByRegionAndInvitee(
            @Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
            @Param("worldId") @NotNull UUID worldId,
            @Param("inviteeId") @NotNull UUID inviteeId);

    @Override
    @Insert("""
            INSERT INTO FreeholdContractAgentInvite (realtyRegionId, inviterId, inviteeId)
            SELECT rr.realtyRegionId, #{inviterId}, #{inviteeId}
            FROM RealtyRegion rr
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            """)
    int insert(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
               @Param("worldId") @NotNull UUID worldId,
               @Param("inviterId") @NotNull UUID inviterId,
               @Param("inviteeId") @NotNull UUID inviteeId);

    @Override
    @Delete("""
            DELETE ai FROM FreeholdContractAgentInvite ai
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = ai.realtyRegionId
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            AND ai.inviteeId = #{inviteeId}
            """)
    int deleteByRegionAndInvitee(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                                  @Param("worldId") @NotNull UUID worldId,
                                  @Param("inviteeId") @NotNull UUID inviteeId);
}
