package io.github.md5sha256.realty.database.maria.mapper;

import io.github.md5sha256.realty.database.entity.RealtyRegionEntity;
import io.github.md5sha256.realty.database.mapper.RealtyRegionMapper;
import org.apache.ibatis.annotations.*;
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
    @Insert("INSERT INTO RealtyRegion (worldGuardRegionId, worldId, contractId) " +
            "VALUES (#{worldGuardRegionId}, #{worldId}, #{contractId})")
    @Options(useGeneratedKeys = true, keyProperty = "realtyRegionId")
    void insert(@NotNull RealtyRegionEntity entity);

    @Override
    @Select("SELECT realtyRegionId, worldGuardRegionId, worldId, contractId " +
            "FROM RealtyRegion WHERE realtyRegionId = #{id}")
    @ConstructorArgs({
            @Arg(column = "realtyRegionId", javaType = int.class),
            @Arg(column = "worldGuardRegionId", javaType = String.class),
            @Arg(column = "worldId", javaType = UUID.class),
            @Arg(column = "contractId", javaType = Integer.class)
    })
    @Nullable RealtyRegionEntity selectById(@Param("id") int id);

    @Override
    @Select("SELECT realtyRegionId, worldGuardRegionId, worldId, contractId " +
            "FROM RealtyRegion WHERE worldId = #{worldId}")
    @ConstructorArgs({
            @Arg(column = "realtyRegionId", javaType = int.class),
            @Arg(column = "worldGuardRegionId", javaType = String.class),
            @Arg(column = "worldId", javaType = UUID.class),
            @Arg(column = "contractId", javaType = Integer.class)
    })
    @NotNull List<RealtyRegionEntity> selectByWorldId(@Param("worldId") @NotNull UUID worldId);

    @Override
    @Select("SELECT realtyRegionId, worldGuardRegionId, worldId, contractId " +
            "FROM RealtyRegion")
    @ConstructorArgs({
            @Arg(column = "realtyRegionId", javaType = int.class),
            @Arg(column = "worldGuardRegionId", javaType = String.class),
            @Arg(column = "worldId", javaType = UUID.class),
            @Arg(column = "contractId", javaType = Integer.class)
    })
    @NotNull List<RealtyRegionEntity> selectAll();

    @Override
    @Update("UPDATE RealtyRegion SET worldGuardRegionId = #{worldGuardRegionId}, " +
            "worldId = #{worldId}, contractId = #{contractId} " +
            "WHERE realtyRegionId = #{realtyRegionId}")
    void update(@NotNull RealtyRegionEntity entity);

    @Override
    @Delete("DELETE FROM RealtyRegion WHERE realtyRegionId = #{id}")
    void deleteById(@Param("id") int id);
}
