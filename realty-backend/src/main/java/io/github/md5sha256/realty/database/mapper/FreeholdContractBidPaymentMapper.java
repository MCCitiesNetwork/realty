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

    /** The payment, if it is still there and its deadline has passed by the database clock. */
    @Nullable FreeholdContractBidPaymentEntity selectExpiredById(int bidId);

    /**
     * Payments, under either rules, that are paid in full (in whole cents) but whose sale was never
     * finalized, as when the finalize failed or the server stopped after the final transfer.
     */
    @NotNull List<FreeholdContractBidPaymentEntity> selectFullyPaidUnfinalized();

    /**
     * Counts an instalment as pending, before its money moves, if nothing else is pending and the
     * confirmed payment is still {@code expectedCurrent}.
     *
     * @return 1 if recorded, 0 if the row changed or another instalment is pending
     */
    int recordPending(int bidId, double expectedCurrent, double amount);

    /** Moves the pending instalment of {@code amount} into the confirmed payment. */
    int confirmPending(@NotNull String worldGuardRegionId, @NotNull UUID worldId, @NotNull UUID bidderId, double amount);

    /** Drops the pending instalment of {@code amount}, as when its transfer failed. */
    int clearPending(@NotNull String worldGuardRegionId, @NotNull UUID worldId, @NotNull UUID bidderId, double amount);

    /** Payments with an instalment pending for more than {@code minutes} by the database clock. */
    @NotNull List<FreeholdContractBidPaymentEntity> selectStuckPending(int minutes);

    /** @param totalDue what the bidder owes in all: the price plus any buyer-paid fees */
    int insertPayment(@NotNull String worldGuardRegionId, @NotNull UUID worldId, @NotNull UUID bidderId, double bidPrice,
                      @NotNull LocalDateTime paymentDeadline, double totalDue);

    /**
     * Offers the auction to the next bidder after a missed payment. Payments are offered in
     * order of each bidder's highest bid, so every bidder with a bid at or above the missed one
     * has already had their turn and is skipped.
     */
    int insertNextPayment(int freeholdContractAuctionId, double missedBidPrice, @NotNull LocalDateTime paymentDeadline);

    /** Sets the total a payment owes, as when a runner-up's fees are locked after it was offered the sale. */
    int updateTotalDue(int bidId, double totalDue);

    int updatePayment(@NotNull String worldGuardRegionId, @NotNull UUID worldId, @NotNull UUID bidderId, double payment);

    int atomicUpdatePayment(@NotNull String worldGuardRegionId, @NotNull UUID worldId,
                            @NotNull UUID bidderId, double expectedCurrent, double newPayment);

    int deleteByBidId(int bidId);

    int deleteByRegion(@NotNull String worldGuardRegionId, @NotNull UUID worldId);

    boolean existsByRegion(@NotNull String worldGuardRegionId, @NotNull UUID worldId);

}
