package io.github.md5sha256.realty.database.maria.mapper;

import io.github.md5sha256.realty.database.entity.FreeholdContractBid;
import io.github.md5sha256.realty.database.mapper.FreeholdContractBidMapper;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;


public interface MariaFreeholdContractBidMapper extends FreeholdContractBidMapper {

    @Override
    @Select("""
            SELECT scb.freeholdContractAuctionId, scb.bidderId, scb.bidPrice, scb.bidTime
            FROM FreeholdContractBid scb
            INNER JOIN FreeholdContractAuction sca ON sca.freeholdContractAuctionId = scb.freeholdContractAuctionId
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = sca.realtyRegionId
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            ORDER BY scb.bidPrice DESC
            LIMIT 1
            """)
    @ConstructorArgs({
            @Arg(column = "freeholdContractAuctionId", javaType = int.class),
            @Arg(column = "bidderId", javaType = UUID.class),
            @Arg(column = "bidPrice", javaType = double.class),
            @Arg(column = "bidTime", javaType = java.time.LocalDateTime.class)
    })
    @Nullable FreeholdContractBid selectHighestBid(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                                               @Param("worldId") @NotNull UUID worldId);

    @Override
    @Select("""
            SELECT DISTINCT scb.bidderId
            FROM FreeholdContractBid scb
            INNER JOIN FreeholdContractAuction sca ON sca.freeholdContractAuctionId = scb.freeholdContractAuctionId
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = sca.realtyRegionId
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            """)
    @NotNull List<UUID> selectDistinctBidders(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                                              @Param("worldId") @NotNull UUID worldId);

    @Override
    @Insert("""
            INSERT INTO FreeholdContractBid (freeholdContractAuctionId, bidderId, bidPrice)
            SELECT #{freeholdContractId}, #{bidderId}, #{bidAmount}
            FROM FreeholdContractAuction sca
            WHERE sca.freeholdContractAuctionId = #{freeholdContractId}
            AND NOW() >= sca.startDate
            AND NOW() < sca.startDate + INTERVAL sca.biddingDurationSeconds SECOND
            AND #{bidAmount} >= sca.minBid
            AND (
                NOT EXISTS (SELECT 1 FROM FreeholdContractBid scb WHERE scb.freeholdContractAuctionId = #{freeholdContractId})
                OR (
                    #{bidAmount} > (SELECT MAX(scb.bidPrice) FROM FreeholdContractBid scb WHERE scb.freeholdContractAuctionId = #{freeholdContractId})
                    AND #{bidAmount} >= (SELECT MAX(scb.bidPrice) + sca.minStep FROM FreeholdContractBid scb WHERE scb.freeholdContractAuctionId = #{freeholdContractId})
                    AND #{bidderId} != (SELECT scb.bidderId FROM FreeholdContractBid scb WHERE scb.freeholdContractAuctionId = #{freeholdContractId} ORDER BY scb.bidPrice DESC LIMIT 1)
                )
            )
            """)
    int performContractBid(@NotNull FreeholdContractBid bid);
}
