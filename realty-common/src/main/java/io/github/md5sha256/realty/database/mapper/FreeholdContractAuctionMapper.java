package io.github.md5sha256.realty.database.mapper;

import io.github.md5sha256.realty.database.entity.FreeholdContractAuctionEntity;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Base mapper interface for query operations on the {@code FreeholdContractAuction} table.
 * SQL annotations are provided by database-specific sub-interfaces.
 *
 * @see FreeholdContractAuctionEntity
 */
public interface FreeholdContractAuctionMapper {

    @Nullable FreeholdContractAuctionEntity selectById(int freeholdContractAuctionId);

    @Nullable FreeholdContractAuctionEntity selectActiveByRegion(@NotNull String worldGuardRegionId, @NotNull UUID worldId);

    int createAuction(@NotNull String worldGuardRegionId, @NotNull UUID worldId, @NotNull UUID auctioneerId, @NotNull LocalDateTime startDate, long biddingDurationSeconds, long paymentDurationSeconds, double minBid, double minStep);

    int postponeAuctionPaymentDeadline(@NotNull String worldGuardRegionId, @NotNull UUID worldId);

    @Nullable List<FreeholdContractAuctionEntity> selectExpiredBiddingAuctions();

    @Nullable List<FreeholdContractAuctionEntity> selectExpiredPaymentAuctions();

    int setPaymentDeadline(int freeholdContractAuctionId, @NotNull LocalDateTime paymentDeadline);

    int markEnded(@Param("freeholdContractAuctionId") int freeholdContractAuctionId);

    int deleteAuction(int freeholdContractAuctionId);

    int deleteActiveAuctionByRegion(@NotNull String worldGuardRegionId, @NotNull UUID worldId);

    boolean existsByRegion(@NotNull String worldGuardRegionId, @NotNull UUID worldId);
}
