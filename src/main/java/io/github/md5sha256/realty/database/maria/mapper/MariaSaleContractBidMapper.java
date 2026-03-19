package io.github.md5sha256.realty.database.maria.mapper;

import io.github.md5sha256.realty.database.entity.SaleContractBid;
import io.github.md5sha256.realty.database.mapper.SaleContractBidMapper;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;


public interface MariaSaleContractBidMapper extends SaleContractBidMapper {

    @Override
    @Select("""
            SELECT scb.saleContractAuctionId, scb.bidderId, scb.bidPrice, scb.bidTime
            FROM SaleContractBid scb
            INNER JOIN SaleContractAuction sca ON sca.saleContractAuctionId = scb.saleContractAuctionId
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = sca.realtyRegionId
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            ORDER BY scb.bidPrice DESC
            LIMIT 1
            """)
    @ConstructorArgs({
            @Arg(column = "saleContractAuctionId", javaType = int.class),
            @Arg(column = "bidderId", javaType = UUID.class),
            @Arg(column = "bidPrice", javaType = double.class),
            @Arg(column = "bidTime", javaType = java.time.LocalDateTime.class)
    })
    @Nullable SaleContractBid selectHighestBid(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                                               @Param("worldId") @NotNull UUID worldId);

    @Override
    @Select("""
            SELECT DISTINCT scb.bidderId
            FROM SaleContractBid scb
            INNER JOIN SaleContractAuction sca ON sca.saleContractAuctionId = scb.saleContractAuctionId
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = sca.realtyRegionId
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            """)
    @NotNull List<UUID> selectDistinctBidders(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                                              @Param("worldId") @NotNull UUID worldId);

    @Override
    @Insert("""
            INSERT INTO SaleContractBid (saleContractAuctionId, bidderId, bidPrice)
            SELECT #{saleContractId}, #{bidderId}, #{bidAmount}
            FROM SaleContractAuction sca
            WHERE sca.saleContractAuctionId = #{saleContractId}
            AND NOW() >= sca.startDate
            AND NOW() < sca.startDate + INTERVAL sca.biddingDurationSeconds SECOND
            AND #{bidAmount} >= sca.minBid
            AND (
                NOT EXISTS (SELECT 1 FROM SaleContractBid scb WHERE scb.saleContractAuctionId = #{saleContractId})
                OR (
                    #{bidAmount} > (SELECT MAX(scb.bidPrice) FROM SaleContractBid scb WHERE scb.saleContractAuctionId = #{saleContractId})
                    AND #{bidAmount} >= (SELECT MAX(scb.bidPrice) + sca.minStep FROM SaleContractBid scb WHERE scb.saleContractAuctionId = #{saleContractId})
                    AND #{bidderId} != (SELECT scb.bidderId FROM SaleContractBid scb WHERE scb.saleContractAuctionId = #{saleContractId} ORDER BY scb.bidPrice DESC LIMIT 1)
                )
            )
            """)
    int performContractBid(@NotNull SaleContractBid bid);
}
