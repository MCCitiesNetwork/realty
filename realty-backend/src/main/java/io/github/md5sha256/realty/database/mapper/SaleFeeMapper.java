package io.github.md5sha256.realty.database.mapper;

import io.github.md5sha256.realty.database.entity.SaleFeeEntity;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Base mapper interface for the {@code SaleFee} table: the lines of money a sale owes its
 * recipients. A line is {@code locked} while its sale is under way, is paid out once the sale
 * completes, and moves through {@code paying} to {@code paid}, or to {@code failed} for a retry.
 * SQL annotations are provided by database-specific sub-interfaces.
 *
 * @see SaleFeeEntity
 */
public interface SaleFeeMapper {

    /**
     * Inserts the line, taking every column but {@code saleFeeId} from {@code row}.
     *
     * @return the new line's {@code saleFeeId}
     */
    int insert(@NotNull SaleFeeEntity row);

    @NotNull List<SaleFeeEntity> selectBySale(@NotNull String saleKind, int saleRef);

    /**
     * Removes a sale's lines that are still locked to it and not yet completed, as when the sale
     * falls through. Lines being paid, paid or failed are kept.
     */
    int deleteUnpaidBySale(@NotNull String saleKind, int saleRef);

    /** Marks a sale's lines as completed, so that they become due for payout. */
    int markCompleted(@NotNull String saleKind, int saleRef);

    /**
     * The lines due for payout: locked lines of a completed sale, and failed lines last tried
     * before {@code retryBefore}, in both cases with fewer than {@code maxAttempts} attempts.
     */
    @NotNull List<SaleFeeEntity> selectDue(@NotNull LocalDateTime retryBefore, int maxAttempts);

    /**
     * Claims the line for payout, moving it from {@code locked} or {@code failed} to {@code paying}.
     *
     * @return 1 if this call claimed the line, 0 if it was not claimable
     */
    int claim(int saleFeeId);

    int markPaid(int saleFeeId);

    /** Records a failed payout: the line becomes {@code failed}, with one more attempt counted. */
    int markFailed(int saleFeeId, @NotNull String error);

}
