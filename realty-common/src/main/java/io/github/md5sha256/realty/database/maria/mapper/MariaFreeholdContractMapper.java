package io.github.md5sha256.realty.database.maria.mapper;

import io.github.md5sha256.realty.database.entity.FreeholdContractEntity;
import io.github.md5sha256.realty.database.mapper.FreeholdContractMapper;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;


/**
 * MariaDB-specific MyBatis mapper for CRUD operations on the {@code FreeholdContract} table.
 *
 * <p>The {@code FreeholdContract} table stores the authority, title-holder, and agreed price for a
 * direct-freehold contract. Its association with a {@code RealtyRegion} is tracked through the
 * {@code Contract} table (managed by {@link MariaContractMapper}); callers must insert the
 * corresponding {@code Contract} row <em>before</em> invoking {@link #insertFreehold} so that
 * referential integrity is maintained at the application level.
 *
 * @see FreeholdContractEntity
 */
public interface MariaFreeholdContractMapper extends FreeholdContractMapper {

    /**
     * {@inheritDoc}
     *
     * <p>Inserts a single row into the {@code FreeholdContract} table. The {@code regionId} parameter
     * is accepted for API consistency (and may be used by callers to look up the region) but is
     * not written to the {@code FreeholdContract} table itself — that linkage is recorded in the
     * {@code Contract} table.
     *
     * <p>The generated {@code freeholdContractId} is set back onto the parameter map by MyBatis via
     * {@code useGeneratedKeys}.
     */
    @Override
    @Select("""
            INSERT INTO FreeholdContract (authorityId, titleHolderId, price)
            VALUES (#{authority}, #{titleHolder}, #{price})
            RETURNING freeholdContractId
            """)
    int insertFreehold(@Param("regionId") int regionId,
                   @Param("price") @Nullable Double price,
                   @Param("authority") @NotNull UUID authority,
                   @Param("titleHolder") @Nullable UUID titleHolder);

    @Override
    @Select("""
            SELECT EXISTS (
                SELECT 1
                FROM FreeholdContract fc
                INNER JOIN Contract c ON c.contractId = fc.freeholdContractId AND c.contractType = 'freehold'
                INNER JOIN RealtyRegion rr ON rr.realtyRegionId = c.realtyRegionId
                WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
                AND rr.worldId = #{worldId}
                AND fc.authorityId = #{playerId}
            )
            """)
    boolean existsByRegionAndAuthority(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                                       @Param("worldId") @NotNull UUID worldId,
                                       @Param("playerId") @NotNull UUID playerId);

    @Override
    @Select("""
            SELECT EXISTS (
                SELECT 1
                FROM FreeholdContract fc
                INNER JOIN Contract c ON c.contractId = fc.freeholdContractId AND c.contractType = 'freehold'
                INNER JOIN RealtyRegion rr ON rr.realtyRegionId = c.realtyRegionId
                WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
                AND rr.worldId = #{worldId}
                AND fc.titleHolderId = #{playerId}
            )
            """)
    boolean existsByRegionAndTitleHolder(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                                         @Param("worldId") @NotNull UUID worldId,
                                         @Param("playerId") @NotNull UUID playerId);

    @Override
    @Select("""
            SELECT fc.freeholdContractId, fc.authorityId, fc.titleHolderId, fc.price
            FROM FreeholdContract fc
            INNER JOIN Contract c ON c.contractId = fc.freeholdContractId AND c.contractType = 'freehold'
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = c.realtyRegionId
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            """)
    @ConstructorArgs({
            @Arg(column = "freeholdContractId", javaType = int.class),
            @Arg(column = "authorityId", javaType = UUID.class),
            @Arg(column = "titleHolderId", javaType = UUID.class),
            @Arg(column = "price", javaType = Double.class)
    })
    @Nullable FreeholdContractEntity selectByRegion(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                                                    @Param("worldId") @NotNull UUID worldId);

    @Override
    @Update("""
            UPDATE FreeholdContract fc
            INNER JOIN Contract c ON c.contractId = fc.freeholdContractId AND c.contractType = 'freehold'
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = c.realtyRegionId
            SET fc.price = #{price}, fc.titleHolderId = #{titleHolder}
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            """)
    int updateFreeholdByRegion(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                           @Param("worldId") @NotNull UUID worldId,
                           @Param("price") double price,
                           @Param("titleHolder") @Nullable UUID titleHolder);

    @Override
    @Update("""
            UPDATE FreeholdContract fc
            INNER JOIN Contract c ON c.contractId = fc.freeholdContractId AND c.contractType = 'freehold'
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = c.realtyRegionId
            SET fc.price = #{price}
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            """)
    int updatePriceByRegion(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                            @Param("worldId") @NotNull UUID worldId,
                            @Param("price") @Nullable Double price);

    @Override
    @Update("""
            UPDATE FreeholdContract fc
            INNER JOIN Contract c ON c.contractId = fc.freeholdContractId AND c.contractType = 'freehold'
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = c.realtyRegionId
            SET fc.titleHolderId = #{titleHolder}
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            """)
    int updateTitleHolderByRegion(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                                  @Param("worldId") @NotNull UUID worldId,
                                  @Param("titleHolder") @Nullable UUID titleHolder);

}
