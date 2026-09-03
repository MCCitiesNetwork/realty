package io.github.md5sha256.realty.database.maria.mapper;

import io.github.md5sha256.realty.database.entity.AuctionSort;
import io.github.md5sha256.realty.database.entity.ActiveAuctionRow;
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
            WHERE sca.freeholdContractAuctionId = #{freeholdContractAuctionId}
            FOR UPDATE
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
    @Nullable FreeholdContractAuctionEntity selectByIdForUpdate(@Param("freeholdContractAuctionId") int freeholdContractAuctionId);

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
    @Select("""
            SELECT sca.freeholdContractAuctionId, sca.realtyRegionId, sca.auctioneerId, sca.startDate, sca.biddingDurationSeconds,
                   sca.paymentDurationSeconds, sca.paymentDeadline, sca.minBid, sca.minStep, sca.ended
            FROM FreeholdContractAuction sca
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = sca.realtyRegionId
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            AND sca.ended = FALSE
            FOR UPDATE
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
    @Nullable FreeholdContractAuctionEntity selectActiveByRegionForUpdate(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
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
            AND NOW() >= COALESCE(
                (SELECT MAX(scb.bidTime) FROM FreeholdContractBid scb WHERE scb.freeholdContractAuctionId = sca.freeholdContractAuctionId),
                sca.startDate
            ) + INTERVAL sca.biddingDurationSeconds SECOND
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
    @Update("""
            UPDATE FreeholdContractAuction
            SET paymentDeadline = #{paymentDeadline}
            WHERE freeholdContractAuctionId = #{freeholdContractAuctionId}
            """)
    int setPaymentDeadline(@Param("freeholdContractAuctionId") int freeholdContractAuctionId,
                           @Param("paymentDeadline") @NotNull LocalDateTime paymentDeadline);

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
            AND sca.ended = FALSE
            """)
    boolean existsByRegion(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                           @Param("worldId") @NotNull UUID worldId);

    @Override
    @Select("""
            SELECT COUNT(*)
            FROM FreeholdContractAuction
            WHERE ended = FALSE
            """)
    int countActive();

    /**
     * The standing bid is read by correlated subquery rather than a join, so an
     * auction with no bids still yields a row -- the same reason the highest-bid
     * columns are nullable together.
     */
    @Override
    @Select("""
            <script>
            SELECT rr.worldGuardRegionId, rr.worldId, sca.auctioneerId, sca.startDate,
                   sca.biddingDurationSeconds, sca.paymentDurationSeconds,
                   sca.minBid, sca.minStep,
                   (SELECT b.bidderId FROM FreeholdContractBid b
                     WHERE b.freeholdContractAuctionId = sca.freeholdContractAuctionId
                     ORDER BY b.bidPrice DESC, b.bidTime DESC LIMIT 1) AS highestBidderId,
                   (SELECT b.bidPrice FROM FreeholdContractBid b
                     WHERE b.freeholdContractAuctionId = sca.freeholdContractAuctionId
                     ORDER BY b.bidPrice DESC, b.bidTime DESC LIMIT 1) AS highestBidPrice,
                   (SELECT b.bidTime FROM FreeholdContractBid b
                     WHERE b.freeholdContractAuctionId = sca.freeholdContractAuctionId
                     ORDER BY b.bidPrice DESC, b.bidTime DESC LIMIT 1) AS highestBidTime,
                   (SELECT COUNT(DISTINCT b.bidderId) FROM FreeholdContractBid b
                     WHERE b.freeholdContractAuctionId = sca.freeholdContractAuctionId) AS bidderCount,
                   DATE_ADD(COALESCE((SELECT b.bidTime FROM FreeholdContractBid b
                                       WHERE b.freeholdContractAuctionId = sca.freeholdContractAuctionId
                                       ORDER BY b.bidPrice DESC, b.bidTime DESC LIMIT 1),
                                     sca.startDate),
                            INTERVAL sca.biddingDurationSeconds SECOND) AS endDate
            FROM FreeholdContractAuction sca
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = sca.realtyRegionId
            WHERE sca.ended = FALSE
            <if test="worldId != null">
            AND rr.worldId = #{worldId}
            </if>
            <choose>
                <when test="sort.name() == 'HIGHEST_BID'">
                ORDER BY COALESCE(highestBidPrice, sca.minBid) DESC, rr.worldGuardRegionId
                </when>
                <otherwise>
                ORDER BY endDate ASC, rr.worldGuardRegionId
                </otherwise>
            </choose>
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    @ConstructorArgs({
            @Arg(column = "worldGuardRegionId", javaType = String.class),
            @Arg(column = "worldId", javaType = UUID.class),
            @Arg(column = "auctioneerId", javaType = UUID.class),
            @Arg(column = "startDate", javaType = LocalDateTime.class),
            @Arg(column = "biddingDurationSeconds", javaType = long.class),
            @Arg(column = "paymentDurationSeconds", javaType = long.class),
            @Arg(column = "minBid", javaType = double.class),
            @Arg(column = "minStep", javaType = double.class),
            @Arg(column = "highestBidderId", javaType = UUID.class),
            @Arg(column = "highestBidPrice", javaType = Double.class),
            @Arg(column = "highestBidTime", javaType = LocalDateTime.class),
            @Arg(column = "bidderCount", javaType = int.class),
            @Arg(column = "endDate", javaType = LocalDateTime.class)
    })
    @NotNull List<ActiveAuctionRow> selectActivePage(@Param("worldId") @Nullable UUID worldId,
                                                     @Param("sort") @NotNull AuctionSort sort,
                                                     @Param("limit") int limit,
                                                     @Param("offset") int offset);

    @Override
    @Select("""
            <script>
            SELECT COUNT(*)
            FROM FreeholdContractAuction sca
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = sca.realtyRegionId
            WHERE sca.ended = FALSE
            <if test="worldId != null">
            AND rr.worldId = #{worldId}
            </if>
            </script>
            """)
    int countActiveInWorld(@Param("worldId") @Nullable UUID worldId);

}
