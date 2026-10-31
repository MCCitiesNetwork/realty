package io.github.md5sha256.realty.database.entity;

import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Internal entity record mapping to the {@code FreeholdContractBidPayment} DDL table.
 *
 * @param bidId                 PK and FK to {@code FreeholdContractBid.bidId}
 * @param freeholdContractAuctionId FK to {@code FreeholdContractAuction.freeholdContractAuctionId}
 * @param realtyRegionId        FK to {@code RealtyRegion.realtyRegionId}
 * @param bidderId              UUID of the player who placed the winning bid
 * @param bidPrice              The winning bid's price (must be &gt; 0)
 * @param paymentDeadline       Deadline by which full payment must be made
 * @param currentPayment        Amount paid so far (0 &le; currentPayment &le; totalDue)
 * @param totalDue              Total the bidder owes: the price plus any buyer-side fees
 * @param oldRules              Whether the payment began before sale fees, and so pays the seller directly
 */
public record FreeholdContractBidPaymentEntity(
        int bidId,
        int freeholdContractAuctionId,
        int realtyRegionId,
        @NotNull UUID bidderId,
        double bidPrice,
        @NotNull LocalDateTime paymentDeadline,
        double currentPayment,
        double totalDue,
        boolean oldRules
) {
}
