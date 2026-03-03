package io.github.md5sha256.realty.database.maria.mapper;

import io.github.md5sha256.realty.database.entity.RealtyRegionEntity;
import io.github.md5sha256.realty.database.mapper.RealtyRegionMapper;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * MyBatis mapper for CRUD operations on the {@code RealtyRegion} table.
 *
 * @see RealtyRegionEntity
 */
public interface MariaRealtyRegionMapper extends RealtyRegionMapper {

    @Override
    @Insert("""
            INSERT INTO RealtyRegion (worldGuardRegionId, worldId)
            VALUES (#{worldGuardRegionId}, #{worldId})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "realtyRegionId", keyColumn = "realtyRegionId")
    int registerWorldGuardRegion(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                                 @Param("worldId") @NotNull UUID worldId);

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

}
