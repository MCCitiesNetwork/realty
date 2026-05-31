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

    /**
     * Locks the auction row by id for the current transaction ({@code FOR UPDATE}).
     * Used as the cross-process serialization point for auction settlement so it
     * cannot interleave with concurrent bidding or cancellation on the same auction.
     */
    @Nullable FreeholdContractAuctionEntity selectByIdForUpdate(int freeholdContractAuctionId);

    @Nullable FreeholdContractAuctionEntity selectActiveByRegion(@NotNull String worldGuardRegionId, @NotNull UUID worldId);

    /**
     * Locks the active auction row for the region for the current transaction
     * ({@code FOR UPDATE}). Used as the cross-process serialization point for bid
     * placement and cancellation so they cannot interleave with settlement or with
     * each other on the same auction.
     */
    @Nullable FreeholdContractAuctionEntity selectActiveByRegionForUpdate(@NotNull String worldGuardRegionId, @NotNull UUID worldId);

    int createAuction(@NotNull String worldGuardRegionId, @NotNull UUID worldId, @NotNull UUID auctioneerId, @NotNull LocalDateTime startDate, long biddingDurationSeconds, long paymentDurationSeconds, double minBid, double minStep);

    int postponeAuctionPaymentDeadline(@NotNull String worldGuardRegionId, @NotNull UUID worldId);

    @Nullable List<FreeholdContractAuctionEntity> selectExpiredBiddingAuctions();

    @Nullable List<FreeholdContractAuctionEntity> selectExpiredPaymentAuctions();

    int setPaymentDeadline(int freeholdContractAuctionId, @NotNull LocalDateTime paymentDeadline);

    int markEnded(@Param("freeholdContractAuctionId") int freeholdContractAuctionId);

    int deleteAuction(int freeholdContractAuctionId);

    int deleteActiveAuctionByRegion(@NotNull String worldGuardRegionId, @NotNull UUID worldId);

    boolean existsByRegion(@NotNull String worldGuardRegionId, @NotNull UUID worldId);

    int countActive();
}
