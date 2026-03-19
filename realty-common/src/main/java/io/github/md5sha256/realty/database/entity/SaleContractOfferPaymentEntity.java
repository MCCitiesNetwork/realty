package io.github.md5sha256.realty.database.entity;

import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Internal entity record mapping to the {@code SaleContractOfferPayment} DDL table.
 *
 * @param offerId         PK and FK to {@code SaleContractOffer.offerId}
 * @param realtyRegionId  FK to {@code RealtyRegion.realtyRegionId}
 * @param offererId       UUID of the player who placed the offer
 * @param offerPrice      Total price due (must be &gt; 0)
 * @param paymentDeadline Deadline by which full payment must be made
 * @param currentPayment  Amount paid so far (0 &le; currentPayment &le; offerPrice)
 */
public record SaleContractOfferPaymentEntity(
        int offerId,
        int realtyRegionId,
        @NotNull UUID offererId,
        double offerPrice,
        @NotNull LocalDateTime paymentDeadline,
        double currentPayment
) {
}
