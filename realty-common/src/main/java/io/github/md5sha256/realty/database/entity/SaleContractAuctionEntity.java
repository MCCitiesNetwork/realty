package io.github.md5sha256.realty.database.entity;

import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Internal entity record mapping to the {@code SaleContractAuction} DDL table.
 *
 * @param saleContractAuctionId  Auto-increment primary key
 * @param startDate              When the auction started
 * @param biddingDurationSeconds Bidding window in seconds (must be &gt; 0)
 * @param paymentDurationSeconds Payment window in seconds (must be &gt; 0)
 * @param minBid                 Minimum bid amount (must be &gt; 0)
 * @param minStep                Minimum price step between bids (must be &gt; 0)
 * @see io.github.md5sha256.realty.api.SaleContractAuction
 */
public record SaleContractAuctionEntity(
        int saleContractAuctionId,
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
