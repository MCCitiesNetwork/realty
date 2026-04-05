package io.github.md5sha256.realty.database.maria.mapper;

import io.github.md5sha256.realty.database.entity.FreeholdContractBidPaymentEntity;
import io.github.md5sha256.realty.database.mapper.FreeholdContractBidPaymentMapper;
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
 * MariaDB-specific MyBatis mapper for query operations on the {@code FreeholdContractBidPayment} table.
 *
 * @see FreeholdContractBidPaymentEntity
 */
public interface MariaFreeholdContractBidPaymentMapper extends FreeholdContractBidPaymentMapper {

    @Override
    @Select("""
            SELECT scbp.bidId, scbp.freeholdContractAuctionId, scbp.realtyRegionId, scbp.bidderId,
                   scbp.bidPrice, scbp.paymentDeadline, scbp.currentPayment
            FROM FreeholdContractBidPayment scbp
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = scbp.realtyRegionId
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            """)
    @ConstructorArgs({
            @Arg(column = "bidId", javaType = int.class),
            @Arg(column = "freeholdContractAuctionId", javaType = int.class),
            @Arg(column = "realtyRegionId", javaType = int.class),
            @Arg(column = "bidderId", javaType = UUID.class),
            @Arg(column = "bidPrice", javaType = double.class),
            @Arg(column = "paymentDeadline", javaType = LocalDateTime.class),
            @Arg(column = "currentPayment", javaType = double.class)
    })
    @Nullable FreeholdContractBidPaymentEntity selectByRegion(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                                                           @Param("worldId") @NotNull UUID worldId);

    @Override
    @Select("""
            SELECT scbp.bidId, scbp.freeholdContractAuctionId, scbp.realtyRegionId, scbp.bidderId,
                   scbp.bidPrice, scbp.paymentDeadline, scbp.currentPayment
            FROM FreeholdContractBidPayment scbp
            WHERE scbp.paymentDeadline < NOW()
            """)
    @ConstructorArgs({
            @Arg(column = "bidId", javaType = int.class),
            @Arg(column = "freeholdContractAuctionId", javaType = int.class),
            @Arg(column = "realtyRegionId", javaType = int.class),
            @Arg(column = "bidderId", javaType = UUID.class),
            @Arg(column = "bidPrice", javaType = double.class),
            @Arg(column = "paymentDeadline", javaType = LocalDateTime.class),
            @Arg(column = "currentPayment", javaType = double.class)
    })
    @NotNull List<FreeholdContractBidPaymentEntity> selectAllExpired();

    @Override
    @Insert("""
            INSERT INTO FreeholdContractBidPayment (bidId, freeholdContractAuctionId, realtyRegionId, bidderId, bidPrice, paymentDeadline, currentPayment)
            SELECT scb.bidId, scb.freeholdContractAuctionId, sca.realtyRegionId, scb.bidderId, scb.bidPrice, #{paymentDeadline}, 0
            FROM FreeholdContractBid scb
            INNER JOIN FreeholdContractAuction sca ON sca.freeholdContractAuctionId = scb.freeholdContractAuctionId
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = sca.realtyRegionId
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            AND scb.bidderId = #{bidderId}
            ORDER BY scb.bidPrice DESC
            LIMIT 1
            """)
    int insertPayment(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                      @Param("worldId") @NotNull UUID worldId,
                      @Param("bidderId") @NotNull UUID bidderId,
                      @Param("bidPrice") double bidPrice,
                      @Param("paymentDeadline") @NotNull LocalDateTime paymentDeadline);

    @Override
    @Insert("""
            INSERT INTO FreeholdContractBidPayment (bidId, freeholdContractAuctionId, realtyRegionId, bidderId, bidPrice, paymentDeadline, currentPayment)
            SELECT scb.bidId, scb.freeholdContractAuctionId, sca.realtyRegionId, scb.bidderId, scb.bidPrice, #{paymentDeadline}, 0
            FROM FreeholdContractBid scb
            INNER JOIN FreeholdContractAuction sca ON sca.freeholdContractAuctionId = scb.freeholdContractAuctionId
            WHERE scb.freeholdContractAuctionId = #{freeholdContractAuctionId}
            AND scb.bidderId != #{excludeBidderId}
            ORDER BY scb.bidPrice DESC
            LIMIT 1
            """)
    int insertNextPayment(@Param("freeholdContractAuctionId") int freeholdContractAuctionId,
                          @Param("excludeBidderId") @NotNull UUID excludeBidderId,
                          @Param("paymentDeadline") @NotNull LocalDateTime paymentDeadline);

    @Override
    @Update("""
            UPDATE FreeholdContractBidPayment scbp
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = scbp.realtyRegionId
            SET scbp.currentPayment = #{payment}
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            AND scbp.bidderId = #{bidderId}
            """)
    int updatePayment(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                      @Param("worldId") @NotNull UUID worldId,
                      @Param("bidderId") @NotNull UUID bidderId,
                      @Param("payment") double payment);

    @Override
    @Delete("""
            DELETE FROM FreeholdContractBidPayment
            WHERE bidId = #{bidId}
            """)
    int deleteByBidId(@Param("bidId") int bidId);

    @Override
    @Delete("""
            DELETE scbp FROM FreeholdContractBidPayment scbp
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = scbp.realtyRegionId
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            """)
    int deleteByRegion(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                       @Param("worldId") @NotNull UUID worldId);

    @Override
    @Select("""
            SELECT COUNT(*) > 0
            FROM FreeholdContractBidPayment scbp
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = scbp.realtyRegionId
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            """)
    boolean existsByRegion(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                           @Param("worldId") @NotNull UUID worldId);

}
