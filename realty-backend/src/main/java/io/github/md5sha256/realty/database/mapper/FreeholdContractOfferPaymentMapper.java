package io.github.md5sha256.realty.database.mapper;

import io.github.md5sha256.realty.database.entity.FreeholdContractOfferPaymentEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Base mapper interface for query operations on the {@code FreeholdContractOfferPayment} table.
 * SQL annotations are provided by database-specific sub-interfaces.
 *
 * @see FreeholdContractOfferPaymentEntity
 */
public interface FreeholdContractOfferPaymentMapper {

    @Nullable FreeholdContractOfferPaymentEntity selectByRegion(@NotNull String worldGuardRegionId, @NotNull UUID worldId);

    @NotNull List<FreeholdContractOfferPaymentEntity> selectAllExpired();

    /** The payment, if it is still there and its deadline has passed by the database clock. */
    @Nullable FreeholdContractOfferPaymentEntity selectExpiredById(int offerId);

    /**
     * Payments under the new rules that are paid in full but whose sale was never finalized, as when
     * the finalize failed or the server stopped after the final transfer.
     */
    @NotNull List<FreeholdContractOfferPaymentEntity> selectFullyPaidUnfinalized();

    /**
     * Counts an instalment as pending, before its money moves, if nothing else is pending and the
     * confirmed payment is still {@code expectedCurrent}.
     *
     * @return 1 if recorded, 0 if the row changed or another instalment is pending
     */
    int recordPending(int offerId, double expectedCurrent, double amount);

    /** Moves the pending instalment of {@code amount} into the confirmed payment. */
    int confirmPending(@NotNull String worldGuardRegionId, @NotNull UUID worldId, @NotNull UUID offererId, double amount);

    /** Drops the pending instalment of {@code amount}, as when its transfer failed. */
    int clearPending(@NotNull String worldGuardRegionId, @NotNull UUID worldId, @NotNull UUID offererId, double amount);

    /** Payments with an instalment pending for more than {@code minutes} by the database clock. */
    @NotNull List<FreeholdContractOfferPaymentEntity> selectStuckPending(int minutes);

    /**
     * @param totalDue   what the offerer owes in all: the price plus any buyer-paid fees
     * @param acceptedBy the player who accepted the offer
     */
    int insertPayment(@NotNull String worldGuardRegionId, @NotNull UUID worldId, @NotNull UUID offererId, double offerPrice,
                      @NotNull LocalDateTime paymentDeadline, double totalDue, @Nullable UUID acceptedBy);

    int updatePayment(@NotNull String worldGuardRegionId, @NotNull UUID worldId, @NotNull UUID offererId, double payment);

    int atomicUpdatePayment(@NotNull String worldGuardRegionId, @NotNull UUID worldId,
                            @NotNull UUID offererId, double expectedCurrent, double newPayment);

    int deleteByOfferId(int offerId);

    int deleteByRegion(@NotNull String worldGuardRegionId, @NotNull UUID worldId);

    boolean existsByRegion(@NotNull String worldGuardRegionId, @NotNull UUID worldId);

}
