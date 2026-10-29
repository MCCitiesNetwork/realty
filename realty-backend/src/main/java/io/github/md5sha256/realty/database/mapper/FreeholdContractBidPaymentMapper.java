package io.github.md5sha256.realty.database.mapper;

import io.github.md5sha256.realty.database.entity.FreeholdContractBidPaymentEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Base mapper interface for query operations on the {@code FreeholdContractBidPayment} table.
 * SQL annotations are provided by database-specific sub-interfaces.
 *
 * @see FreeholdContractBidPaymentEntity
 */
public interface FreeholdContractBidPaymentMapper {

    @Nullable FreeholdContractBidPaymentEntity selectByRegion(@NotNull String worldGuardRegionId, @NotNull UUID worldId);

    @NotNull List<FreeholdContractBidPaymentEntity> selectAllExpired();

    int insertPayment(@NotNull String worldGuardRegionId, @NotNull UUID worldId, @NotNull UUID bidderId, double bidPrice, @NotNull LocalDateTime paymentDeadline);

    /**
     * Offers the auction to the next bidder after a missed payment. Payments are offered in
     * order of each bidder's highest bid, so every bidder with a bid at or above the missed one
     * has already had their turn and is skipped.
     */
    int insertNextPayment(int freeholdContractAuctionId, double missedBidPrice, @NotNull LocalDateTime paymentDeadline);

    int updatePayment(@NotNull String worldGuardRegionId, @NotNull UUID worldId, @NotNull UUID bidderId, double payment);

    int atomicUpdatePayment(@NotNull String worldGuardRegionId, @NotNull UUID worldId,
                            @NotNull UUID bidderId, double expectedCurrent, double newPayment);

    int deleteByBidId(int bidId);

    int deleteByRegion(@NotNull String worldGuardRegionId, @NotNull UUID worldId);

    boolean existsByRegion(@NotNull String worldGuardRegionId, @NotNull UUID worldId);

}
