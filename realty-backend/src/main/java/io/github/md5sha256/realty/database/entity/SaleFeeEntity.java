package io.github.md5sha256.realty.database.entity;

import io.github.md5sha256.realty.api.FeeSide;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Internal entity record mapping to the {@code SaleFee} DDL table: one line of money a sale owes a
 * recipient, either a fee or the seller's proceeds.
 *
 * @param saleFeeId        PK; ignored on insert
 * @param saleKind         {@code buy}, {@code bid} or {@code offer}
 * @param saleRef          the bidId or offerId; null for a buy
 * @param realtyRegionId   FK to {@code RealtyRegion.realtyRegionId}; null once the region is deleted
 * @param regionName       the WorldGuard region id, kept for when the region is gone
 * @param worldId          the region's world
 * @param buyerId          the buying player
 * @param lineKind         {@code fee} or {@code proceeds}
 * @param source           what the line is for, such as a levy's name or a fee's role
 * @param recipientPartyId FK to {@code Party.partyId}; the party the line is paid to
 * @param side             who pays the line
 * @param percent          the line's percentage of the price
 * @param amount           the amount paid to the recipient (&ge; 0)
 * @param status           {@code locked}, {@code paying}, {@code paid} or {@code failed}
 * @param completedAt      when the sale completed; a locked line is paid out only once this is set
 * @param attempts         failed payout attempts so far
 * @param lastAttemptAt    when the last payout attempt failed
 * @param lastError        why the last payout attempt failed
 */
public record SaleFeeEntity(
        int saleFeeId,
        @NotNull String saleKind,
        @Nullable Integer saleRef,
        @Nullable Integer realtyRegionId,
        @NotNull String regionName,
        @NotNull UUID worldId,
        @NotNull UUID buyerId,
        @NotNull String lineKind,
        @NotNull String source,
        int recipientPartyId,
        @NotNull FeeSide side,
        double percent,
        double amount,
        @NotNull String status,
        @Nullable LocalDateTime completedAt,
        int attempts,
        @Nullable LocalDateTime lastAttemptAt,
        @Nullable String lastError
) {
}
