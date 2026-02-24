package io.github.md5sha256.realty.database.maria.mapper;

import io.github.md5sha256.realty.database.entity.ContractEntity;
import io.github.md5sha256.realty.database.mapper.ContractMapper;
import org.apache.ibatis.annotations.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * MariaDB-specific MyBatis mapper for CRUD operations on the {@code Contract} table.
 * <p>
 * The table has a composite primary key {@code (contractId, contractType)},
 * but {@code contractId} is auto-increment and globally unique, so single-column
 * lookups by {@code contractId} are used for convenience.
 *
 * @see ContractEntity
 */
public interface MariaContractMapper extends ContractMapper {

    @Override
    @Insert("INSERT INTO Contract (contractType, realtyRegionId) " +
            "VALUES (#{contractType}, #{realtyRegionId})")
    @Options(useGeneratedKeys = true, keyProperty = "contractId")
    void insert(@NotNull ContractEntity entity);

    @Override
    @Select("SELECT contractId, contractType, realtyRegionId " +
            "FROM Contract WHERE contractId = #{id}")
    @ConstructorArgs({
            @Arg(column = "contractId", javaType = int.class),
            @Arg(column = "contractType", javaType = String.class),
            @Arg(column = "realtyRegionId", javaType = int.class)
    })
    @Nullable ContractEntity selectById(@Param("id") int id);

    @Override
    @Select("SELECT contractId, contractType, realtyRegionId " +
            "FROM Contract WHERE realtyRegionId = #{realtyRegionId}")
    @ConstructorArgs({
            @Arg(column = "contractId", javaType = int.class),
            @Arg(column = "contractType", javaType = String.class),
            @Arg(column = "realtyRegionId", javaType = int.class)
    })
    @NotNull List<ContractEntity> selectByRealtyRegionId(@Param("realtyRegionId") int realtyRegionId);

    @Override
    @Select("SELECT contractId, contractType, realtyRegionId FROM Contract")
    @ConstructorArgs({
            @Arg(column = "contractId", javaType = int.class),
            @Arg(column = "contractType", javaType = String.class),
            @Arg(column = "realtyRegionId", javaType = int.class)
    })
    @NotNull List<ContractEntity> selectAll();

    @Override
    @Update("UPDATE Contract SET contractType = #{contractType}, " +
            "realtyRegionId = #{realtyRegionId} " +
            "WHERE contractId = #{contractId}")
    void update(@NotNull ContractEntity entity);

    @Override
    @Delete("DELETE FROM Contract WHERE contractId = #{id}")
    void deleteById(@Param("id") int id);
}
