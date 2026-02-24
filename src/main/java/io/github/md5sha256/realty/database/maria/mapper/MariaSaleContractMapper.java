package io.github.md5sha256.realty.database.maria.mapper;

import io.github.md5sha256.realty.database.entity.SaleContractEntity;
import io.github.md5sha256.realty.database.mapper.SaleContractMapper;
import org.apache.ibatis.annotations.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * MariaDB-specific MyBatis mapper for CRUD operations on the {@code SaleContract} table.
 *
 * @see SaleContractEntity
 */
public interface MariaSaleContractMapper extends SaleContractMapper {

    @Override
    @Insert("INSERT INTO SaleContract (authorityId, titleHolderId, price) " +
            "VALUES (#{authorityId}, #{titleHolderId}, #{price})")
    @Options(useGeneratedKeys = true, keyProperty = "saleContractId")
    void insert(@NotNull SaleContractEntity entity);

    @Override
    @Select("SELECT saleContractId, authorityId, titleHolderId, price " +
            "FROM SaleContract WHERE saleContractId = #{id}")
    @ConstructorArgs({
            @Arg(column = "saleContractId", javaType = int.class),
            @Arg(column = "authorityId", javaType = UUID.class),
            @Arg(column = "titleHolderId", javaType = UUID.class),
            @Arg(column = "price", javaType = double.class)
    })
    @Nullable SaleContractEntity selectById(@Param("id") int id);

    @Override
    @Select("SELECT saleContractId, authorityId, titleHolderId, price " +
            "FROM SaleContract WHERE titleHolderId = #{titleHolderId}")
    @ConstructorArgs({
            @Arg(column = "saleContractId", javaType = int.class),
            @Arg(column = "authorityId", javaType = UUID.class),
            @Arg(column = "titleHolderId", javaType = UUID.class),
            @Arg(column = "price", javaType = double.class)
    })
    @NotNull List<SaleContractEntity> selectByTitleHolderId(@Param("titleHolderId") @NotNull UUID titleHolderId);

    @Override
    @Select("SELECT saleContractId, authorityId, titleHolderId, price FROM SaleContract")
    @ConstructorArgs({
            @Arg(column = "saleContractId", javaType = int.class),
            @Arg(column = "authorityId", javaType = UUID.class),
            @Arg(column = "titleHolderId", javaType = UUID.class),
            @Arg(column = "price", javaType = double.class)
    })
    @NotNull List<SaleContractEntity> selectAll();

    @Override
    @Update("UPDATE SaleContract SET authorityId = #{authorityId}, " +
            "titleHolderId = #{titleHolderId}, price = #{price} " +
            "WHERE saleContractId = #{saleContractId}")
    void update(@NotNull SaleContractEntity entity);

    @Override
    @Delete("DELETE FROM SaleContract WHERE saleContractId = #{id}")
    void deleteById(@Param("id") int id);
}
