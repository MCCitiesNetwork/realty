package io.github.md5sha256.realty.database.mapper;

import io.github.md5sha256.realty.database.entity.PendingRefundEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Base mapper interface for the {@code PendingRefund} table: refunds owed to buyers that could not
 * be paid when they fell due. A refund is {@code pending} until claimed for payout, and is deleted
 * once paid.
 * SQL annotations are provided by database-specific sub-interfaces.
 *
 * @see PendingRefundEntity
 */
public interface PendingRefundMapper {

    /** @return the new refund's {@code pendingRefundId} */
    int insert(@NotNull UUID buyerId, double amount, @NotNull String regionName,
               @Nullable UUID worldId, @NotNull String reason);

    /**
     * The refunds due for payout: pending refunds never tried or last tried more than
     * {@code retryAfterMinutes} ago by the database clock, with fewer than {@code maxAttempts}
     * attempts.
     */
    @NotNull List<PendingRefundEntity> selectDue(int retryAfterMinutes, int maxAttempts);

    /**
     * The refunds claimed for payout and never deleted or marked failed, as after a crash
     * mid-payout. Whether their money moved is unknown, so they are never retried automatically.
     */
    @NotNull List<PendingRefundEntity> selectPaying();

    /**
     * Claims the refund for payout, moving it from {@code pending} to {@code paying}.
     *
     * @return 1 if this call claimed the refund, 0 if it was not claimable
     */
    int claim(int pendingRefundId);

    int delete(int pendingRefundId);

    /** Records a failed payout: the refund returns to {@code pending}, with one more attempt counted. */
    int markFailed(int pendingRefundId, @NotNull String error);

}
