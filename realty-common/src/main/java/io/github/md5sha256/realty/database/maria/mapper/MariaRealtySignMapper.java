package io.github.md5sha256.realty.database.maria.mapper;

import io.github.md5sha256.realty.database.entity.RealtySignEntity;
import io.github.md5sha256.realty.database.mapper.RealtySignMapper;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * MariaDB-specific MyBatis mapper for the {@code RealtySign} table.
 *
 * @see RealtySignEntity
 */
public interface MariaRealtySignMapper extends RealtySignMapper {

    @Override
    @Insert("""
            INSERT INTO RealtySign (worldId, blockX, blockY, blockZ, realtyRegionId)
            SELECT #{worldId}, #{blockX}, #{blockY}, #{blockZ}, rr.realtyRegionId
            FROM RealtyRegion rr
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{regionWorldId}
            """)
    int insert(@Param("worldId") @NotNull UUID worldId,
               @Param("blockX") int blockX,
               @Param("blockY") int blockY,
               @Param("blockZ") int blockZ,
               @Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
               @Param("regionWorldId") @NotNull UUID regionWorldId);

    @Override
    @Select("""
            SELECT worldId, blockX, blockY, blockZ, realtyRegionId
            FROM RealtySign
            WHERE worldId = #{worldId}
            AND blockX = #{blockX}
            AND blockY = #{blockY}
            AND blockZ = #{blockZ}
            """)
    @ConstructorArgs({
            @Arg(column = "worldId", javaType = UUID.class),
            @Arg(column = "blockX", javaType = int.class),
            @Arg(column = "blockY", javaType = int.class),
            @Arg(column = "blockZ", javaType = int.class),
            @Arg(column = "realtyRegionId", javaType = int.class)
    })
    @Nullable RealtySignEntity selectByPosition(@Param("worldId") @NotNull UUID worldId,
                                                 @Param("blockX") int blockX,
                                                 @Param("blockY") int blockY,
                                                 @Param("blockZ") int blockZ);

    @Override
    @Select("""
            SELECT worldId, blockX, blockY, blockZ, realtyRegionId
            FROM RealtySign
            WHERE worldId = #{worldId}
            AND FLOOR(blockX / 16) = #{chunkX}
            AND FLOOR(blockZ / 16) = #{chunkZ}
            """)
    @ConstructorArgs({
            @Arg(column = "worldId", javaType = UUID.class),
            @Arg(column = "blockX", javaType = int.class),
            @Arg(column = "blockY", javaType = int.class),
            @Arg(column = "blockZ", javaType = int.class),
            @Arg(column = "realtyRegionId", javaType = int.class)
    })
    @NotNull List<RealtySignEntity> selectByChunk(@Param("worldId") @NotNull UUID worldId,
                                                   @Param("chunkX") int chunkX,
                                                   @Param("chunkZ") int chunkZ);

    @Override
    @Select("""
            SELECT rs.worldId, rs.blockX, rs.blockY, rs.blockZ, rs.realtyRegionId
            FROM RealtySign rs
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = rs.realtyRegionId
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            """)
    @ConstructorArgs({
            @Arg(column = "worldId", javaType = UUID.class),
            @Arg(column = "blockX", javaType = int.class),
            @Arg(column = "blockY", javaType = int.class),
            @Arg(column = "blockZ", javaType = int.class),
            @Arg(column = "realtyRegionId", javaType = int.class)
    })
    @NotNull List<RealtySignEntity> selectByRegion(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                                                    @Param("worldId") @NotNull UUID worldId);

    @Override
    @Delete("""
            DELETE FROM RealtySign
            WHERE worldId = #{worldId}
            AND blockX = #{blockX}
            AND blockY = #{blockY}
            AND blockZ = #{blockZ}
            """)
    int deleteByPosition(@Param("worldId") @NotNull UUID worldId,
                         @Param("blockX") int blockX,
                         @Param("blockY") int blockY,
                         @Param("blockZ") int blockZ);

    @Override
    @Delete("""
            DELETE rs FROM RealtySign rs
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = rs.realtyRegionId
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            """)
    int deleteByRegion(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                       @Param("worldId") @NotNull UUID worldId);
}
