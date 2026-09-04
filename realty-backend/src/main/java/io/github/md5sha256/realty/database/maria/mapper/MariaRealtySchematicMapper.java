package io.github.md5sha256.realty.database.maria.mapper;

import io.github.md5sha256.realty.database.entity.RealtySchematicEntity;
import io.github.md5sha256.realty.database.mapper.RealtySchematicMapper;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * MyBatis mapper for the {@code RealtySchematic} table.
 *
 * @see RealtySchematicEntity
 */
public interface MariaRealtySchematicMapper extends RealtySchematicMapper {

    /**
     * The insert selects its {@code realtyRegionId} from {@code RealtyRegion} rather
     * than taking one as a parameter, so an unregistered region affects zero rows
     * instead of inserting a row that references nothing.
     */
    @Override
    @Insert("""
            INSERT INTO RealtySchematic (realtyRegionId, data, capturedAt)
            SELECT r.realtyRegionId, #{data}, #{capturedAt}
            FROM RealtyRegion r
            WHERE r.worldGuardRegionId = #{worldGuardRegionId}
              AND r.worldId = #{worldId}
            ON DUPLICATE KEY UPDATE data = #{data},
                                    capturedAt = #{capturedAt}
            """)
    int upsert(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
               @Param("worldId") @NotNull UUID worldId,
               @Param("data") byte @NotNull [] data,
               @Param("capturedAt") @NotNull LocalDateTime capturedAt);

    @Override
    @Select("""
            SELECT s.realtyRegionId, s.data, s.capturedAt
            FROM RealtySchematic s
            JOIN RealtyRegion r ON r.realtyRegionId = s.realtyRegionId
            WHERE r.worldGuardRegionId = #{worldGuardRegionId}
              AND r.worldId = #{worldId}
            """)
    @ConstructorArgs({
            @Arg(column = "realtyRegionId", javaType = int.class),
            @Arg(column = "data", javaType = byte[].class),
            @Arg(column = "capturedAt", javaType = LocalDateTime.class)
    })
    @Nullable RealtySchematicEntity selectByWorldGuardRegion(
            @Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
            @Param("worldId") @NotNull UUID worldId);

}
