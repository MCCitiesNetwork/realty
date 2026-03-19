package io.github.md5sha256.realty.database.maria.mapper;

import io.github.md5sha256.realty.database.entity.SaleContractEntity;
import io.github.md5sha256.realty.database.mapper.SaleContractMapper;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;


/**
 * MariaDB-specific MyBatis mapper for CRUD operations on the {@code SaleContract} table.
 *
 * <p>The {@code SaleContract} table stores the authority, title-holder, and agreed price for a
 * direct-sale contract. Its association with a {@code RealtyRegion} is tracked through the
 * {@code Contract} table (managed by {@link MariaContractMapper}); callers must insert the
 * corresponding {@code Contract} row <em>before</em> invoking {@link #insertSale} so that
 * referential integrity is maintained at the application level.
 *
 * @see SaleContractEntity
 */
public interface MariaSaleContractMapper extends SaleContractMapper {

    /**
     * {@inheritDoc}
     *
     * <p>Inserts a single row into the {@code SaleContract} table. The {@code regionId} parameter
     * is accepted for API consistency (and may be used by callers to look up the region) but is
     * not written to the {@code SaleContract} table itself — that linkage is recorded in the
     * {@code Contract} table.
     *
     * <p>The generated {@code saleContractId} is set back onto the parameter map by MyBatis via
     * {@code useGeneratedKeys}.
     */
    @Override
    @Select("""
            INSERT INTO SaleContract (authorityId, titleHolderId, price)
            VALUES (#{authority}, #{titleHolder}, #{price})
            RETURNING saleContractId
            """)
    int insertSale(@Param("regionId") int regionId,
                   @Param("price") @Nullable Double price,
                   @Param("authority") @NotNull UUID authority,
                   @Param("titleHolder") @Nullable UUID titleHolder);

    @Override
    @Select("""
            SELECT EXISTS (
                SELECT 1
                FROM SaleContract sc
                INNER JOIN Contract c ON c.contractId = sc.saleContractId AND c.contractType = 'sale'
                INNER JOIN RealtyRegion rr ON rr.realtyRegionId = c.realtyRegionId
                WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
                AND rr.worldId = #{worldId}
                AND sc.authorityId = #{playerId}
            )
            """)
    boolean existsByRegionAndAuthority(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                                       @Param("worldId") @NotNull UUID worldId,
                                       @Param("playerId") @NotNull UUID playerId);

    @Override
    @Select("""
            SELECT sc.saleContractId, sc.authorityId, sc.titleHolderId, sc.price
            FROM SaleContract sc
            INNER JOIN Contract c ON c.contractId = sc.saleContractId AND c.contractType = 'sale'
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = c.realtyRegionId
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            """)
    @ConstructorArgs({
            @Arg(column = "saleContractId", javaType = int.class),
            @Arg(column = "authorityId", javaType = UUID.class),
            @Arg(column = "titleHolderId", javaType = UUID.class),
            @Arg(column = "price", javaType = Double.class)
    })
    @Nullable SaleContractEntity selectByRegion(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                                               @Param("worldId") @NotNull UUID worldId);

    @Override
    @Update("""
            UPDATE SaleContract sc
            INNER JOIN Contract c ON c.contractId = sc.saleContractId AND c.contractType = 'sale'
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = c.realtyRegionId
            SET sc.price = #{price}, sc.titleHolderId = #{titleHolder}
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            """)
    int updateSaleByRegion(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                           @Param("worldId") @NotNull UUID worldId,
                           @Param("price") double price,
                           @Param("titleHolder") @Nullable UUID titleHolder);

    @Override
    @Update("""
            UPDATE SaleContract sc
            INNER JOIN Contract c ON c.contractId = sc.saleContractId AND c.contractType = 'sale'
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = c.realtyRegionId
            SET sc.price = #{price}
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            """)
    int updatePriceByRegion(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                            @Param("worldId") @NotNull UUID worldId,
                            @Param("price") @Nullable Double price);

}
