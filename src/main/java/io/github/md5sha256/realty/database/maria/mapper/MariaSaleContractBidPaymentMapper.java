package io.github.md5sha256.realty.database.maria.mapper;

import io.github.md5sha256.realty.database.entity.SaleContractBidPaymentEntity;
import io.github.md5sha256.realty.database.mapper.SaleContractBidPaymentMapper;
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
import java.util.UUID;

/**
 * MariaDB-specific MyBatis mapper for query operations on the {@code SaleContractBidPayment} table.
 *
 * @see SaleContractBidPaymentEntity
 */
public interface MariaSaleContractBidPaymentMapper extends SaleContractBidPaymentMapper {

    @Override
    @Select("""
            SELECT scbp.bidId, scbp.saleContractAuctionId, scbp.realtyRegionId, scbp.bidderId,
                   scbp.bidPrice, scbp.paymentDeadline, scbp.currentPayment
            FROM SaleContractBidPayment scbp
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = scbp.realtyRegionId
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            """)
    @ConstructorArgs({
            @Arg(column = "bidId", javaType = int.class),
            @Arg(column = "saleContractAuctionId", javaType = int.class),
            @Arg(column = "realtyRegionId", javaType = int.class),
            @Arg(column = "bidderId", javaType = UUID.class),
            @Arg(column = "bidPrice", javaType = double.class),
            @Arg(column = "paymentDeadline", javaType = LocalDateTime.class),
            @Arg(column = "currentPayment", javaType = double.class)
    })
    @Nullable SaleContractBidPaymentEntity selectByRegion(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                                                           @Param("worldId") @NotNull UUID worldId);

    @Override
    @Insert("""
            INSERT INTO SaleContractBidPayment (bidId, saleContractAuctionId, realtyRegionId, bidderId, bidPrice, paymentDeadline, currentPayment)
            SELECT scb.bidId, scb.saleContractAuctionId, sca.realtyRegionId, scb.bidderId, scb.bidPrice, #{paymentDeadline}, 0
            FROM SaleContractBid scb
            INNER JOIN SaleContractAuction sca ON sca.saleContractAuctionId = scb.saleContractAuctionId
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
    @Update("""
            UPDATE SaleContractBidPayment scbp
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
            DELETE scbp FROM SaleContractBidPayment scbp
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = scbp.realtyRegionId
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            """)
    int deleteByRegion(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                       @Param("worldId") @NotNull UUID worldId);

    @Override
    @Select("""
            SELECT COUNT(*) > 0
            FROM SaleContractBidPayment scbp
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = scbp.realtyRegionId
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            """)
    boolean existsByRegion(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                           @Param("worldId") @NotNull UUID worldId);

}
