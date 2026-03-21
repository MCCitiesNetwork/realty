package io.github.md5sha256.realty.database.maria.mapper;

import io.github.md5sha256.realty.database.entity.FreeholdContractAuctionEntity;
import io.github.md5sha256.realty.database.mapper.FreeholdContractAuctionMapper;
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
 * MariaDB-specific MyBatis mapper for query operations on the {@code FreeholdContractAuction} table.
 *
 * @see FreeholdContractAuctionEntity
 */
public interface MariaFreeholdContractAuctionMapper extends FreeholdContractAuctionMapper {

    @Override
    @Select("""
            SELECT sca.freeholdContractAuctionId, sca.realtyRegionId, sca.auctioneerId, sca.startDate, sca.biddingDurationSeconds,
                   sca.paymentDurationSeconds, sca.paymentDeadline, sca.minBid, sca.minStep, sca.ended
            FROM FreeholdContractAuction sca
            WHERE sca.freeholdContractAuctionId = #{freeholdContractAuctionId}
            """)
    @ConstructorArgs({
            @Arg(column = "freeholdContractAuctionId", javaType = int.class),
            @Arg(column = "realtyRegionId", javaType = int.class),
            @Arg(column = "auctioneerId", javaType = UUID.class),
            @Arg(column = "startDate", javaType = LocalDateTime.class),
            @Arg(column = "biddingDurationSeconds", javaType = long.class),
            @Arg(column = "paymentDurationSeconds", javaType = long.class),
            @Arg(column = "paymentDeadline", javaType = LocalDateTime.class),
            @Arg(column = "minBid", javaType = double.class),
            @Arg(column = "minStep", javaType = double.class),
            @Arg(column = "ended", javaType = boolean.class)
    })
    @Nullable FreeholdContractAuctionEntity selectById(@Param("freeholdContractAuctionId") int freeholdContractAuctionId);

    @Override
    @Select("""
            SELECT sca.freeholdContractAuctionId, sca.realtyRegionId, sca.auctioneerId, sca.startDate, sca.biddingDurationSeconds,
                   sca.paymentDurationSeconds, sca.paymentDeadline, sca.minBid, sca.minStep, sca.ended
            FROM FreeholdContractAuction sca
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = sca.realtyRegionId
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            AND sca.ended = FALSE
            """)
    @ConstructorArgs({
            @Arg(column = "freeholdContractAuctionId", javaType = int.class),
            @Arg(column = "realtyRegionId", javaType = int.class),
            @Arg(column = "auctioneerId", javaType = UUID.class),
            @Arg(column = "startDate", javaType = LocalDateTime.class),
            @Arg(column = "biddingDurationSeconds", javaType = long.class),
            @Arg(column = "paymentDurationSeconds", javaType = long.class),
            @Arg(column = "paymentDeadline", javaType = LocalDateTime.class),
            @Arg(column = "minBid", javaType = double.class),
            @Arg(column = "minStep", javaType = double.class),
            @Arg(column = "ended", javaType = boolean.class)
    })
    @Nullable FreeholdContractAuctionEntity selectActiveByRegion(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                                                             @Param("worldId") @NotNull UUID worldId);

    @Override
    @Insert("""
            INSERT INTO FreeholdContractAuction (realtyRegionId, auctioneerId, startDate, biddingDurationSeconds, paymentDurationSeconds, minBid, minStep)
            SELECT rr.realtyRegionId, #{auctioneerId}, NOW(), #{biddingDurationSeconds}, #{paymentDurationSeconds}, #{minBid}, #{minStep}
            FROM RealtyRegion rr
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            """)
    int createAuction(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                      @Param("worldId") @NotNull UUID worldId,
                      @Param("auctioneerId") @NotNull UUID auctioneerId,
                      @Param("startDate") @NotNull LocalDateTime startDate,
                      @Param("biddingDurationSeconds") long biddingDurationSeconds,
                      @Param("paymentDurationSeconds") long paymentDurationSeconds,
                      @Param("minBid") double minBid,
                      @Param("minStep") double minStep);

    @Override
    @Update("""
            UPDATE FreeholdContractAuction sca
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = sca.realtyRegionId
            SET sca.paymentDeadline = sca.paymentDeadline + INTERVAL sca.paymentDurationSeconds SECOND
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            """)
    int postponeAuctionPaymentDeadline(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                                       @Param("worldId") @NotNull UUID worldId);

    @Override
    @Select("""
            SELECT sca.freeholdContractAuctionId, sca.realtyRegionId, sca.auctioneerId, sca.startDate, sca.biddingDurationSeconds,
                   sca.paymentDurationSeconds, sca.paymentDeadline, sca.minBid, sca.minStep, sca.ended
            FROM FreeholdContractAuction sca
            WHERE sca.ended = FALSE
            AND NOW() >= sca.startDate + INTERVAL sca.biddingDurationSeconds SECOND
            """)
    @ConstructorArgs({
            @Arg(column = "freeholdContractAuctionId", javaType = int.class),
            @Arg(column = "realtyRegionId", javaType = int.class),
            @Arg(column = "auctioneerId", javaType = UUID.class),
            @Arg(column = "startDate", javaType = LocalDateTime.class),
            @Arg(column = "biddingDurationSeconds", javaType = long.class),
            @Arg(column = "paymentDurationSeconds", javaType = long.class),
            @Arg(column = "paymentDeadline", javaType = LocalDateTime.class),
            @Arg(column = "minBid", javaType = double.class),
            @Arg(column = "minStep", javaType = double.class),
            @Arg(column = "ended", javaType = boolean.class)
    })
    @Nullable List<FreeholdContractAuctionEntity> selectExpiredBiddingAuctions();

    @Override
    @Select("""
            SELECT sca.freeholdContractAuctionId, sca.realtyRegionId, sca.auctioneerId, sca.startDate, sca.biddingDurationSeconds,
                   sca.paymentDurationSeconds, sca.paymentDeadline, sca.minBid, sca.minStep, sca.ended
            FROM FreeholdContractAuction sca
            WHERE sca.ended = FALSE
            AND NOW() >= sca.paymentDeadline
            """)
    @ConstructorArgs({
            @Arg(column = "freeholdContractAuctionId", javaType = int.class),
            @Arg(column = "realtyRegionId", javaType = int.class),
            @Arg(column = "auctioneerId", javaType = UUID.class),
            @Arg(column = "startDate", javaType = LocalDateTime.class),
            @Arg(column = "biddingDurationSeconds", javaType = long.class),
            @Arg(column = "paymentDurationSeconds", javaType = long.class),
            @Arg(column = "paymentDeadline", javaType = LocalDateTime.class),
            @Arg(column = "minBid", javaType = double.class),
            @Arg(column = "minStep", javaType = double.class),
            @Arg(column = "ended", javaType = boolean.class)
    })
    @Nullable List<FreeholdContractAuctionEntity> selectExpiredPaymentAuctions();

    @Override
    @Update("UPDATE FreeholdContractAuction SET ended = TRUE WHERE freeholdContractAuctionId = #{freeholdContractAuctionId}")
    int markEnded(@Param("freeholdContractAuctionId") int freeholdContractAuctionId);

    @Override
    @Delete("DELETE FROM FreeholdContractAuction WHERE freeholdContractAuctionId = #{freeholdContractAuctionId}")
    int deleteAuction(@Param("freeholdContractAuctionId") int freeholdContractAuctionId);

    @Override
    @Delete("""
            DELETE sca FROM FreeholdContractAuction sca
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
            FROM FreeholdContractAuction sca
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = sca.realtyRegionId
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            """)
    boolean existsByRegion(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                           @Param("worldId") @NotNull UUID worldId);

}
