package io.github.md5sha256.realty.database.entity;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Internal entity record mapping to the {@code FreeholdContractOfferPayment} DDL table.
 *
 * @param offerId         PK and FK to {@code FreeholdContractOffer.offerId}
 * @param realtyRegionId  FK to {@code RealtyRegion.realtyRegionId}
 * @param offererId       UUID of the player who placed the offer
 * @param offerPrice      The offer's price (must be &gt; 0)
 * @param paymentDeadline Deadline by which full payment must be made
 * @param currentPayment  Amount paid so far (0 &le; currentPayment &le; totalDue)
 * @param totalDue        Total the offerer owes: the price plus any buyer-side fees
 * @param oldRules        Whether the payment began before sale fees, and so pays the seller directly
 * @param acceptedBy      The player who accepted the offer; null for payments from before sale fees
 * @param pendingPayment  An instalment counted but whose money has not yet moved; not yet paid
 * @param pendingSince    When the pending instalment was counted, by the database clock
 */
public record FreeholdContractOfferPaymentEntity(
        int offerId,
        int realtyRegionId,
        @NotNull UUID offererId,
        double offerPrice,
        @NotNull LocalDateTime paymentDeadline,
        double currentPayment,
        double totalDue,
        boolean oldRules,
        @Nullable UUID acceptedBy,
        double pendingPayment,
        @Nullable LocalDateTime pendingSince
) {
}
