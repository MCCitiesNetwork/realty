package io.github.md5sha256.realty.database.maria.mapper;

import io.github.md5sha256.realty.database.mapper.FreeholdContractSanctionedAuctioneerMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * MariaDB-specific MyBatis mapper for the {@code FreeholdContractSanctionedAuctioneers} table.
 *
 * @see io.github.md5sha256.realty.database.entity.FreeholdContractSanctionedAuctioneerEntity
 */
public interface MariaFreeholdContractSanctionedAuctioneerMapper extends FreeholdContractSanctionedAuctioneerMapper {

    @Override
    @Select("""
            SELECT COUNT(*) > 0
            FROM FreeholdContractSanctionedAuctioneers sca
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = sca.realtyRegionId
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            AND sca.auctioneerId = #{auctioneerId}
            """)
    boolean existsByRegionAndAuctioneer(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                                        @Param("worldId") @NotNull UUID worldId,
                                        @Param("auctioneerId") @NotNull UUID auctioneerId);

    @Override
    @Insert("""
            INSERT INTO FreeholdContractSanctionedAuctioneers (realtyRegionId, auctioneerId)
            SELECT rr.realtyRegionId, #{auctioneerId}
            FROM RealtyRegion rr
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            """)
    int insert(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
               @Param("worldId") @NotNull UUID worldId,
               @Param("auctioneerId") @NotNull UUID auctioneerId);

    @Override
    @Delete("""
            DELETE sca FROM FreeholdContractSanctionedAuctioneers sca
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = sca.realtyRegionId
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            AND sca.auctioneerId = #{auctioneerId}
            """)
    int deleteByRegionAndAuctioneer(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                                     @Param("worldId") @NotNull UUID worldId,
                                     @Param("auctioneerId") @NotNull UUID auctioneerId);

    @Override
    @Delete("""
            DELETE sca FROM FreeholdContractSanctionedAuctioneers sca
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = sca.realtyRegionId
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            """)
    int deleteAllByRegion(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                          @Param("worldId") @NotNull UUID worldId);
}
