package io.github.md5sha256.realty.database.mapper;

import io.github.md5sha256.realty.database.entity.SaleContractAuctionEntity;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Base mapper interface for query operations on the {@code SaleContractAuction} table.
 * SQL annotations are provided by database-specific sub-interfaces.
 *
 * @see SaleContractAuctionEntity
 */
public interface SaleContractAuctionMapper {

    @Nullable SaleContractAuctionEntity selectById(int saleContractAuctionId);

    @Nullable SaleContractAuctionEntity selectActiveByRegion(@NotNull String worldGuardRegionId, @NotNull UUID worldId);

    int createAuction(@NotNull String worldGuardRegionId, @NotNull UUID worldId, @NotNull LocalDateTime startDate, long biddingDurationSeconds, long paymentDurationSeconds, double minBid, double minStep);

    int postponeAuctionPaymentDeadline(@NotNull String worldGuardRegionId, @NotNull UUID worldId);

    @Nullable List<SaleContractAuctionEntity> selectExpiredBiddingAuctions();

    @Nullable List<SaleContractAuctionEntity> selectExpiredPaymentAuctions();

    int markEnded(@Param("saleContractAuctionId") int saleContractAuctionId);

    int deleteAuction(int saleContractAuctionId);

    int deleteActiveAuctionByRegion(@NotNull String worldGuardRegionId, @NotNull UUID worldId);

    boolean existsByRegion(@NotNull String worldGuardRegionId, @NotNull UUID worldId);
}
