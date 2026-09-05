package io.github.md5sha256.realty.database.maria.mapper;

import io.github.md5sha256.realty.database.entity.StatisticsEntity;
import io.github.md5sha256.realty.database.mapper.StatisticsMapper;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Select;
import org.jetbrains.annotations.NotNull;

/**
 * Every figure below is the same expression its standalone counter uses -- see
 * {@code countAll}, {@code countOccupied}, {@code averagePrice} and {@code countActive}
 * on the contract, offer, auction and region mappers -- so the two can never disagree.
 */
public interface MariaStatisticsMapper extends StatisticsMapper {

    @Override
    @Select("""
            SELECT
                (SELECT COUNT(*) FROM RealtyRegion) AS regions,
                (SELECT COUNT(*) FROM FreeholdContract) AS freeholdContracts,
                (SELECT COUNT(*) FROM FreeholdContract WHERE titleHolderId IS NOT NULL) AS occupiedFreeholds,
                (SELECT COALESCE(AVG(price), 0) FROM FreeholdContract WHERE price IS NOT NULL) AS averageFreeholdPrice,
                (SELECT COUNT(*) FROM LeaseholdContract) AS leaseholdContracts,
                (SELECT COUNT(*) FROM LeaseholdContract WHERE tenantId IS NOT NULL) AS occupiedLeaseholds,
                (SELECT COALESCE(AVG(price), 0) FROM LeaseholdContract) AS averageLeaseholdPrice,
                (SELECT COALESCE(AVG(TIMESTAMPDIFF(SECOND, startDate, endDate)), 0)
                 FROM LeaseholdContract WHERE tenantId IS NOT NULL AND startDate IS NOT NULL) AS averageLeaseholdDurationSeconds,
                (SELECT COUNT(*) FROM FreeholdContractOffer) AS activeOffers,
                (SELECT COUNT(*) FROM FreeholdContractAuction WHERE ended = FALSE) AS activeAuctions
            """)
    @ConstructorArgs({
            @Arg(column = "regions", javaType = int.class),
            @Arg(column = "freeholdContracts", javaType = int.class),
            @Arg(column = "occupiedFreeholds", javaType = int.class),
            @Arg(column = "averageFreeholdPrice", javaType = double.class),
            @Arg(column = "leaseholdContracts", javaType = int.class),
            @Arg(column = "occupiedLeaseholds", javaType = int.class),
            @Arg(column = "averageLeaseholdPrice", javaType = double.class),
            @Arg(column = "averageLeaseholdDurationSeconds", javaType = long.class),
            @Arg(column = "activeOffers", javaType = int.class),
            @Arg(column = "activeAuctions", javaType = int.class)
    })
    @NotNull StatisticsEntity select();
}
