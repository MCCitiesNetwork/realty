package io.github.md5sha256.realty.database.maria.mapper;

import io.github.md5sha256.realty.database.entity.SaleContractAuctionEntity;
import io.github.md5sha256.realty.database.mapper.SaleContractAuctionMapper;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * MariaDB-specific MyBatis mapper for query operations on the {@code SaleContractAuction} table.
 *
 * @see SaleContractAuctionEntity
 */
public interface MariaSaleContractAuctionMapper extends SaleContractAuctionMapper {

    @Override
    @Select("""
            SELECT sca.saleContractAuctionId, sca.realtyRegionId, sca.startDate, sca.biddingDurationSeconds,
                   sca.paymentDurationSeconds, sca.paymentDeadline, sca.minBid, sca.minStep, sca.ended
            FROM SaleContractAuction sca
            WHERE sca.saleContractAuctionId = #{saleContractAuctionId}
            """)
    @ConstructorArgs({
            @Arg(column = "saleContractAuctionId", javaType = int.class),
            @Arg(column = "realtyRegionId", javaType = int.class),
            @Arg(column = "startDate", javaType = LocalDateTime.class),
            @Arg(column = "biddingDurationSeconds", javaType = long.class),
            @Arg(column = "paymentDurationSeconds", javaType = long.class),
            @Arg(column = "paymentDeadline", javaType = LocalDateTime.class),
            @Arg(column = "minBid", javaType = double.class),
            @Arg(column = "minStep", javaType = double.class),
            @Arg(column = "ended", javaType = boolean.class)
    })
    @Nullable SaleContractAuctionEntity selectById(@Param("saleContractAuctionId") int saleContractAuctionId);

    @Override
    @Select("""
            SELECT sca.saleContractAuctionId, sca.realtyRegionId, sca.startDate, sca.biddingDurationSeconds,
                   sca.paymentDurationSeconds, sca.paymentDeadline, sca.minBid, sca.minStep, sca.ended
            FROM SaleContractAuction sca
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = sca.realtyRegionId
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            AND sca.ended = FALSE
            """)
    @ConstructorArgs({
            @Arg(column = "saleContractAuctionId", javaType = int.class),
            @Arg(column = "realtyRegionId", javaType = int.class),
            @Arg(column = "startDate", javaType = LocalDateTime.class),
            @Arg(column = "biddingDurationSeconds", javaType = long.class),
            @Arg(column = "paymentDurationSeconds", javaType = long.class),
            @Arg(column = "paymentDeadline", javaType = LocalDateTime.class),
            @Arg(column = "minBid", javaType = double.class),
            @Arg(column = "minStep", javaType = double.class),
            @Arg(column = "ended", javaType = boolean.class)
    })
    @Nullable SaleContractAuctionEntity selectActiveByRegion(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                                                             @Param("worldId") @NotNull UUID worldId);

    @Override
    @Insert("""
            INSERT INTO SaleContractAuction (realtyRegionId, startDate, biddingDurationSeconds, paymentDurationSeconds, minBid, minStep)
            SELECT rr.realtyRegionId, NOW(), #{biddingDurationSeconds}, #{paymentDurationSeconds}, #{minBid}, #{minStep}
            FROM RealtyRegion rr
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            """)
    int createAuction(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                      @Param("worldId") @NotNull UUID worldId,
                      @Param("startDate") @NotNull LocalDateTime startDate,
                      @Param("biddingDurationSeconds") long biddingDurationSeconds,
                      @Param("paymentDurationSeconds") long paymentDurationSeconds,
                      @Param("minBid") double minBid,
                      @Param("minStep") double minStep);

    @Override
    @Update("""
            UPDATE SaleContractAuction sca
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = sca.realtyRegionId
            SET sca.paymentDeadline = sca.paymentDeadline + INTERVAL sca.paymentDurationSeconds SECOND
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            """)
    int postponeAuctionPaymentDeadline(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                                       @Param("worldId") @NotNull UUID worldId);

    @Override
    @Select("""
            SELECT sca.saleContractAuctionId, sca.realtyRegionId, sca.startDate, sca.biddingDurationSeconds,
                   sca.paymentDurationSeconds, sca.paymentDeadline, sca.minBid, sca.minStep, sca.ended
            FROM SaleContractAuction sca
            WHERE sca.ended = FALSE
            AND NOW() >= sca.startDate + INTERVAL sca.biddingDurationSeconds SECOND
            """)
    @ConstructorArgs({
            @Arg(column = "saleContractAuctionId", javaType = int.class),
            @Arg(column = "realtyRegionId", javaType = int.class),
            @Arg(column = "startDate", javaType = LocalDateTime.class),
            @Arg(column = "biddingDurationSeconds", javaType = long.class),
            @Arg(column = "paymentDurationSeconds", javaType = long.class),
            @Arg(column = "paymentDeadline", javaType = LocalDateTime.class),
            @Arg(column = "minBid", javaType = double.class),
            @Arg(column = "minStep", javaType = double.class),
            @Arg(column = "ended", javaType = boolean.class)
    })
    @Nullable List<SaleContractAuctionEntity> selectExpiredBiddingAuctions();

    @Override
    @Select("""
            SELECT sca.saleContractAuctionId, sca.realtyRegionId, sca.startDate, sca.biddingDurationSeconds,
                   sca.paymentDurationSeconds, sca.paymentDeadline, sca.minBid, sca.minStep, sca.ended
            FROM SaleContractAuction sca
            WHERE sca.ended = FALSE
            AND NOW() >= sca.paymentDeadline
            """)
    @ConstructorArgs({
            @Arg(column = "saleContractAuctionId", javaType = int.class),
            @Arg(column = "realtyRegionId", javaType = int.class),
            @Arg(column = "startDate", javaType = LocalDateTime.class),
            @Arg(column = "biddingDurationSeconds", javaType = long.class),
            @Arg(column = "paymentDurationSeconds", javaType = long.class),
            @Arg(column = "paymentDeadline", javaType = LocalDateTime.class),
            @Arg(column = "minBid", javaType = double.class),
            @Arg(column = "minStep", javaType = double.class),
            @Arg(column = "ended", javaType = boolean.class)
    })
    @Nullable List<SaleContractAuctionEntity> selectExpiredPaymentAuctions();

    @Override
    @Update("UPDATE SaleContractAuction SET ended = TRUE WHERE saleContractAuctionId = #{saleContractAuctionId}")
    int markEnded(@Param("saleContractAuctionId") int saleContractAuctionId);

    @Override
    @Delete("DELETE FROM SaleContractAuction WHERE saleContractAuctionId = #{saleContractAuctionId}")
    int deleteAuction(@Param("saleContractAuctionId") int saleContractAuctionId);

    @Override
    @Delete("""
            DELETE sca FROM SaleContractAuction sca
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = sca.realtyRegionId
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            AND sca.ended = FALSE
            """)
    int deleteActiveAuctionByRegion(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                                    @Param("worldId") @NotNull UUID worldId);

    @Override
    @Select("""
            SELECT COUNT(*) > 0
            FROM SaleContractAuction sca
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = sca.realtyRegionId
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            """)
    boolean existsByRegion(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                           @Param("worldId") @NotNull UUID worldId);

}
