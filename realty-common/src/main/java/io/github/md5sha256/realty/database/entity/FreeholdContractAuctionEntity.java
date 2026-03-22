package io.github.md5sha256.realty.database.entity;

import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Internal entity record mapping to the {@code FreeholdContractAuction} DDL table.
 *
 * @param freeholdContractAuctionId  Auto-increment primary key
 * @param startDate              When the auction started
 * @param biddingDurationSeconds Maximum time between bids in seconds (must be &gt; 0).
 *                               If no bid is placed within this duration since the last bid
 *                               (or since startDate if there are no bids), the auction ends.
 * @param paymentDurationSeconds Payment window in seconds (must be &gt; 0)
 * @param minBid                 Minimum bid amount (must be &gt; 0)
 * @param minStep                Minimum price step between bids (must be &gt; 0)
 * @see io.github.md5sha256.realty.api.FreeholdContractAuction
 */
public record FreeholdContractAuctionEntity(
        int freeholdContractAuctionId,
        int realtyRegionId,
        @NotNull UUID auctioneerId,
        @NotNull LocalDateTime startDate,
        long biddingDurationSeconds,
        long paymentDurationSeconds,
        @NotNull LocalDateTime paymentDeadline,
        double minBid,
        double minStep,
        boolean ended
) {
}
