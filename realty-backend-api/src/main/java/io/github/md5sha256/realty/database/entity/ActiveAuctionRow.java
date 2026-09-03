package io.github.md5sha256.realty.database.entity;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One live auction, joined to its region and its standing bid by a single query.
 *
 * <p>The three {@code highestBid*} fields are null together, for an auction nobody has
 * bid on yet.</p>
 *
 * @param endDate the bidding deadline, computed in SQL as the last bid's time -- or
 *                {@code startDate} where there is none -- plus
 *                {@code biddingDurationSeconds}. Derived in the query rather than in
 *                Java because it is also what {@code ENDING_SOON} orders by, and a
 *                sort key computed after paging would order only the page
 */
public record ActiveAuctionRow(
        @NotNull String worldGuardRegionId,
        @NotNull UUID worldId,
        @NotNull UUID auctioneerId,
        @NotNull LocalDateTime startDate,
        long biddingDurationSeconds,
        long paymentDurationSeconds,
        double minBid,
        double minStep,
        @Nullable UUID highestBidderId,
        @Nullable Double highestBidPrice,
        @Nullable LocalDateTime highestBidTime,
        int bidderCount,
        @NotNull LocalDateTime endDate
) {
}
