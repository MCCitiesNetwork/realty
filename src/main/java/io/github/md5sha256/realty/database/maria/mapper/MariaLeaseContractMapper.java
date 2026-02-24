package io.github.md5sha256.realty.database.maria.mapper;

import io.github.md5sha256.realty.database.entity.LeaseContractEntity;
import io.github.md5sha256.realty.database.mapper.LeaseContractMapper;
import org.apache.ibatis.annotations.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * MariaDB-specific MyBatis mapper for CRUD operations on the {@code LeaseContract} table.
 *
 * @see LeaseContractEntity
 */
public interface MariaLeaseContractMapper extends LeaseContractMapper {

    @Override
    @Insert("INSERT INTO LeaseContract (tenantId, price, durationSeconds, startDate, " +
            "currentMaxExtensions, maxExtensions) " +
            "VALUES (#{tenantId}, #{price}, #{durationSeconds}, #{startDate}, " +
            "#{currentMaxExtensions}, #{maxExtensions})")
    @Options(useGeneratedKeys = true, keyProperty = "leaseContractId")
    void insert(@NotNull LeaseContractEntity entity);

    @Override
    @Select("SELECT leaseContractId, tenantId, price, durationSeconds, startDate, " +
            "currentMaxExtensions, maxExtensions " +
            "FROM LeaseContract WHERE leaseContractId = #{id}")
    @ConstructorArgs({
            @Arg(column = "leaseContractId", javaType = int.class),
            @Arg(column = "tenantId", javaType = UUID.class),
            @Arg(column = "price", javaType = double.class),
            @Arg(column = "durationSeconds", javaType = long.class),
            @Arg(column = "startDate", javaType = LocalDateTime.class),
            @Arg(column = "currentMaxExtensions", javaType = Integer.class),
            @Arg(column = "maxExtensions", javaType = Integer.class)
    })
    @Nullable LeaseContractEntity selectById(@Param("id") int id);

    @Override
    @Select("SELECT leaseContractId, tenantId, price, durationSeconds, startDate, " +
            "currentMaxExtensions, maxExtensions " +
            "FROM LeaseContract WHERE tenantId = #{tenantId}")
    @ConstructorArgs({
            @Arg(column = "leaseContractId", javaType = int.class),
            @Arg(column = "tenantId", javaType = UUID.class),
            @Arg(column = "price", javaType = double.class),
            @Arg(column = "durationSeconds", javaType = long.class),
            @Arg(column = "startDate", javaType = LocalDateTime.class),
            @Arg(column = "currentMaxExtensions", javaType = Integer.class),
            @Arg(column = "maxExtensions", javaType = Integer.class)
    })
    @NotNull List<LeaseContractEntity> selectByTenantId(@Param("tenantId") @NotNull UUID tenantId);

    @Override
    @Select("SELECT leaseContractId, tenantId, price, durationSeconds, startDate, " +
            "currentMaxExtensions, maxExtensions FROM LeaseContract")
    @ConstructorArgs({
            @Arg(column = "leaseContractId", javaType = int.class),
            @Arg(column = "tenantId", javaType = UUID.class),
            @Arg(column = "price", javaType = double.class),
            @Arg(column = "durationSeconds", javaType = long.class),
            @Arg(column = "startDate", javaType = LocalDateTime.class),
            @Arg(column = "currentMaxExtensions", javaType = Integer.class),
            @Arg(column = "maxExtensions", javaType = Integer.class)
    })
    @NotNull List<LeaseContractEntity> selectAll();

    @Override
    @Update("UPDATE LeaseContract SET tenantId = #{tenantId}, price = #{price}, " +
            "durationSeconds = #{durationSeconds}, startDate = #{startDate}, " +
            "currentMaxExtensions = #{currentMaxExtensions}, maxExtensions = #{maxExtensions} " +
            "WHERE leaseContractId = #{leaseContractId}")
    void update(@NotNull LeaseContractEntity entity);

    @Override
    @Delete("DELETE FROM LeaseContract WHERE leaseContractId = #{id}")
    void deleteById(@Param("id") int id);
}
