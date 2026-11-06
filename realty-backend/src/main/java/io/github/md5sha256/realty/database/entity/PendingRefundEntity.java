package io.github.md5sha256.realty.database.entity;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Internal entity record mapping to the {@code PendingRefund} DDL table: money owed back to a buyer
 * whose refund could not be paid.
 *
 * @param pendingRefundId PK
 * @param buyerId         the player owed the refund
 * @param amount          the amount owed (&gt; 0)
 * @param regionName      the WorldGuard region id the money was paid towards
 * @param worldId         the region's world, if known
 * @param reason          {@code bid-expired}, {@code offer-expired} or {@code region-deleted}
 * @param status          {@code pending} or {@code paying}
 * @param attempts        failed payout attempts so far
 * @param lastAttemptAt   when the last payout attempt failed
 * @param lastError       why the last payout attempt failed
 */
public record PendingRefundEntity(
        int pendingRefundId,
        @NotNull UUID buyerId,
        double amount,
        @NotNull String regionName,
        @Nullable UUID worldId,
        @NotNull String reason,
        @NotNull String status,
        int attempts,
        @Nullable LocalDateTime lastAttemptAt,
        @Nullable String lastError
) {
}
