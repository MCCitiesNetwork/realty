package io.github.md5sha256.realty.database.maria.mapper;

import io.github.md5sha256.realty.database.entity.RealtyWorldEntity;
import io.github.md5sha256.realty.database.mapper.RealtyWorldMapper;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public interface MariaRealtyWorldMapper extends RealtyWorldMapper {

    @Override
    @Insert("""
            INSERT INTO RealtyWorld (worldId, worldName)
            VALUES (#{worldId}, #{worldName})
            ON DUPLICATE KEY UPDATE worldName = #{worldName}
            """)
    void upsert(@Param("worldId") @NotNull UUID worldId,
                @Param("worldName") @NotNull String worldName);

    @Override
    @Select("""
            SELECT worldId, worldName
            FROM RealtyWorld
            ORDER BY worldName
            """)
    @ConstructorArgs({
            @Arg(column = "worldId", javaType = UUID.class),
            @Arg(column = "worldName", javaType = String.class)
    })
    @NotNull List<RealtyWorldEntity> selectAll();

    @Override
    @Select("""
            SELECT worldId, worldName
            FROM RealtyWorld
            WHERE worldId = #{worldId}
            """)
    @ConstructorArgs({
            @Arg(column = "worldId", javaType = UUID.class),
            @Arg(column = "worldName", javaType = String.class)
    })
    @Nullable RealtyWorldEntity selectById(@Param("worldId") @NotNull UUID worldId);

    @Override
    @Select("""
            SELECT worldId, worldName
            FROM RealtyWorld
            WHERE worldName = #{worldName}
            LIMIT 1
            """)
    @ConstructorArgs({
            @Arg(column = "worldId", javaType = UUID.class),
            @Arg(column = "worldName", javaType = String.class)
    })
    @Nullable RealtyWorldEntity selectByName(@Param("worldName") @NotNull String worldName);

}
