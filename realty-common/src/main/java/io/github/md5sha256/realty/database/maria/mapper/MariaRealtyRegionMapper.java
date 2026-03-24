package io.github.md5sha256.realty.database.maria.mapper;

import io.github.md5sha256.realty.database.entity.RealtyRegionEntity;
import io.github.md5sha256.realty.database.mapper.RealtyRegionMapper;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * MyBatis mapper for CRUD operations on the {@code RealtyRegion} table.
 *
 * @see RealtyRegionEntity
 */
public interface MariaRealtyRegionMapper extends RealtyRegionMapper {

    @Override
    @Select("""
            INSERT INTO RealtyRegion (worldGuardRegionId, worldId)
            VALUES (#{worldGuardRegionId}, #{worldId})
            RETURNING realtyRegionId
            """)
    int registerWorldGuardRegion(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                                 @Param("worldId") @NotNull UUID worldId);

    @Override
    @Select("""
            SELECT realtyRegionId, worldGuardRegionId, worldId
            FROM RealtyRegion
            WHERE realtyRegionId = #{realtyRegionId}
            """)
    @ConstructorArgs({
            @Arg(column = "realtyRegionId", javaType = int.class),
            @Arg(column = "worldGuardRegionId", javaType = String.class),
            @Arg(column = "worldId", javaType = UUID.class)
    })
    @Nullable RealtyRegionEntity selectById(@Param("realtyRegionId") int realtyRegionId);

    @Override
    @Select("""
            SELECT realtyRegionId, worldGuardRegionId, worldId
            FROM RealtyRegion
            WHERE worldGuardRegionId = #{worldGuardRegionId}
            AND worldId = #{worldId}
            """)
    @ConstructorArgs({
            @Arg(column = "realtyRegionId", javaType = int.class),
            @Arg(column = "worldGuardRegionId", javaType = String.class),
            @Arg(column = "worldId", javaType = UUID.class)
    })
    @Nullable RealtyRegionEntity selectByWorldGuardRegion(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                                                         @Param("worldId") @NotNull UUID worldId);

    @Override
    @Delete("""
            DELETE FROM RealtyRegion
            WHERE worldGuardRegionId = #{worldGuardRegionId}
            AND worldId = #{worldId}
            """)
    int deleteByWorldGuardRegion(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                                 @Param("worldId") @NotNull UUID worldId);

    @Override
    @Delete("""
            DELETE FROM RealtyRegion
            WHERE realtyRegionId = #{realtyRegionId}
            """)
    int deleteByRealtyRegionId(@Param("realtyRegionId") int realtyRegionId);

    @Override
    @Select("""
            SELECT realtyRegionId, worldGuardRegionId, worldId
            FROM RealtyRegion
            """)
    @ConstructorArgs({
            @Arg(column = "realtyRegionId", javaType = int.class),
            @Arg(column = "worldGuardRegionId", javaType = String.class),
            @Arg(column = "worldId", javaType = UUID.class)
    })
    @NotNull List<RealtyRegionEntity> selectAll();

    @Override
    @Select("""
            SELECT rr.realtyRegionId, rr.worldGuardRegionId, rr.worldId
            FROM RealtyRegion rr
            INNER JOIN Contract c ON c.realtyRegionId = rr.realtyRegionId AND c.contractType = 'freehold'
            INNER JOIN FreeholdContract fc ON fc.freeholdContractId = c.contractId
            WHERE fc.titleHolderId = #{playerId}
            LIMIT #{limit} OFFSET #{offset}
            """)
    @ConstructorArgs({
            @Arg(column = "realtyRegionId", javaType = int.class),
            @Arg(column = "worldGuardRegionId", javaType = String.class),
            @Arg(column = "worldId", javaType = UUID.class)
    })
    @NotNull List<RealtyRegionEntity> selectRegionsByTitleHolder(@Param("playerId") @NotNull UUID playerId,
                                                                 @Param("limit") int limit,
                                                                 @Param("offset") int offset);

    @Override
    @Select("""
            SELECT rr.realtyRegionId, rr.worldGuardRegionId, rr.worldId
            FROM RealtyRegion rr
            INNER JOIN Contract c ON c.realtyRegionId = rr.realtyRegionId AND c.contractType = 'freehold'
            INNER JOIN FreeholdContract fc ON fc.freeholdContractId = c.contractId
            WHERE fc.authorityId = #{playerId}
            LIMIT #{limit} OFFSET #{offset}
            """)
    @ConstructorArgs({
            @Arg(column = "realtyRegionId", javaType = int.class),
            @Arg(column = "worldGuardRegionId", javaType = String.class),
            @Arg(column = "worldId", javaType = UUID.class)
    })
    @NotNull List<RealtyRegionEntity> selectRegionsByAuthority(@Param("playerId") @NotNull UUID playerId,
                                                               @Param("limit") int limit,
                                                               @Param("offset") int offset);

    @Override
    @Select("""
            SELECT rr.realtyRegionId, rr.worldGuardRegionId, rr.worldId
            FROM RealtyRegion rr
            INNER JOIN Contract c ON c.realtyRegionId = rr.realtyRegionId AND c.contractType = 'leasehold'
            INNER JOIN LeaseholdContract lc ON lc.leaseholdContractId = c.contractId
            WHERE lc.tenantId = #{playerId}
            LIMIT #{limit} OFFSET #{offset}
            """)
    @ConstructorArgs({
            @Arg(column = "realtyRegionId", javaType = int.class),
            @Arg(column = "worldGuardRegionId", javaType = String.class),
            @Arg(column = "worldId", javaType = UUID.class)
    })
    @NotNull List<RealtyRegionEntity> selectRegionsByTenant(@Param("playerId") @NotNull UUID playerId,
                                                            @Param("limit") int limit,
                                                            @Param("offset") int offset);

    @Override
    @Select("""
            SELECT COUNT(*)
            FROM RealtyRegion rr
            INNER JOIN Contract c ON c.realtyRegionId = rr.realtyRegionId AND c.contractType = 'freehold'
            INNER JOIN FreeholdContract fc ON fc.freeholdContractId = c.contractId
            WHERE fc.titleHolderId = #{playerId}
            """)
    int countRegionsByTitleHolder(@Param("playerId") @NotNull UUID playerId);

    @Override
    @Select("""
            SELECT COUNT(*)
            FROM RealtyRegion rr
            INNER JOIN Contract c ON c.realtyRegionId = rr.realtyRegionId AND c.contractType = 'freehold'
            INNER JOIN FreeholdContract fc ON fc.freeholdContractId = c.contractId
            WHERE fc.authorityId = #{playerId}
            """)
    int countRegionsByAuthority(@Param("playerId") @NotNull UUID playerId);

    @Override
    @Select("""
            SELECT COUNT(*)
            FROM RealtyRegion rr
            INNER JOIN Contract c ON c.realtyRegionId = rr.realtyRegionId AND c.contractType = 'leasehold'
            INNER JOIN LeaseholdContract lc ON lc.leaseholdContractId = c.contractId
            WHERE lc.tenantId = #{playerId}
            """)
    int countRegionsByTenant(@Param("playerId") @NotNull UUID playerId);

}
