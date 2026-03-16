package io.github.md5sha256.realty.database.maria.mapper;

import io.github.md5sha256.realty.database.entity.SaleContractOfferEntity;
import io.github.md5sha256.realty.database.mapper.SaleContractOfferMapper;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * MariaDB-specific MyBatis mapper for query operations on the {@code SaleContractOffer} table.
 *
 * @see SaleContractOfferEntity
 */
public interface MariaSaleContractOfferMapper extends SaleContractOfferMapper {

    @Override
    @Select("""
            SELECT sco.offerId, sco.realtyRegionId, sco.offererId, sco.offerPrice, sco.offerTime
            FROM SaleContractOffer sco
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = sco.realtyRegionId
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            """)
    @ConstructorArgs({
            @Arg(column = "offerId", javaType = int.class),
            @Arg(column = "realtyRegionId", javaType = int.class),
            @Arg(column = "offererId", javaType = UUID.class),
            @Arg(column = "offerPrice", javaType = double.class),
            @Arg(column = "offerTime", javaType = LocalDateTime.class)
    })
    @Nullable List<SaleContractOfferEntity> selectByRegion(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                                                           @Param("worldId") @NotNull UUID worldId);

    @Override
    @Insert("""
            INSERT INTO SaleContractOffer (realtyRegionId, offererId, offerPrice)
            SELECT rr.realtyRegionId, #{offererId}, #{offerPrice}
            FROM RealtyRegion rr
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            """)
    @Options(useGeneratedKeys = true, keyProperty = "offerId")
    int insertOffer(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                    @Param("worldId") @NotNull UUID worldId,
                    @Param("offererId") @NotNull UUID offererId,
                    @Param("offerPrice") double offerPrice);

    @Override
    @Delete("""
            DELETE sco
            FROM SaleContractOffer sco
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = sco.realtyRegionId
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            """)
    int deleteOffers(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                     @Param("worldId") @NotNull UUID worldId);

    @Override
    @Delete("""
            DELETE sco
            FROM SaleContractOffer sco
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = sco.realtyRegionId
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            AND sco.offererId = #{offererId}
            """)
    int deleteOfferByOfferer(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                             @Param("worldId") @NotNull UUID worldId,
                             @Param("offererId") @NotNull UUID offererId);

    @Override
    @Select("""
            SELECT COUNT(*) > 0
            FROM SaleContractOffer sco
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = sco.realtyRegionId
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            AND sco.offererId = #{offererId}
            """)
    boolean existsByOfferer(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                            @Param("worldId") @NotNull UUID worldId,
                            @Param("offererId") @NotNull UUID offererId);

}
