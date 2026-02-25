package io.github.md5sha256.realty.database.maria.mapper;

import io.github.md5sha256.realty.database.entity.SaleContractAuctionEntity;
import io.github.md5sha256.realty.database.mapper.SaleContractAuctionMapper;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * MariaDB-specific MyBatis mapper for query operations on the {@code SaleContractAuction} table.
 *
 * @see SaleContractAuctionEntity
 */
public interface MariaSaleContractAuctionMapper extends SaleContractAuctionMapper {

    @Override
    @Select("""
            SELECT sca.saleContractAuctionId, sca.startDate, sca.biddingDurationSeconds,
                   sca.paymentDurationSeconds, sca.paymentDeadline, sca.minBid, sca.minStep
            FROM SaleContractAuction sca
            INNER JOIN Contract c ON c.contractId = sca.saleContractAuctionId
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = c.realtyRegionId
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            """)
    @ConstructorArgs({
            @Arg(column = "saleContractAuctionId", javaType = int.class),
            @Arg(column = "startDate", javaType = LocalDateTime.class),
            @Arg(column = "biddingDurationSeconds", javaType = long.class),
            @Arg(column = "paymentDurationSeconds", javaType = long.class),
            @Arg(column = "paymentDeadline", javaType = LocalDateTime.class),
            @Arg(column = "minBid", javaType = double.class),
            @Arg(column = "minStep", javaType = double.class)
    })
    @Nullable SaleContractAuctionEntity selectByRegion(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                                                       @Param("worldId") @NotNull UUID worldId);

}
