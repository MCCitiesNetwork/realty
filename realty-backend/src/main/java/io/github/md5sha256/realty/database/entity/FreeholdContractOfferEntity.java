package io.github.md5sha256.realty.database.entity;

import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Internal entity record mapping to the {@code FreeholdContractOffer} DDL table.
 *
 * @param offerId        Auto-increment primary key
 * @param realtyRegionId FK to {@code RealtyRegion.realtyRegionId}
 * @param offererId      UUID of the player who placed the offer
 * @param offerPrice     Offered price (must be &gt; 0)
 * @param offerTime      Timestamp when the offer was placed (set by DB default)
 */
public record FreeholdContractOfferEntity(
        int offerId,
        int realtyRegionId,
        @NotNull UUID offererId,
        double offerPrice,
        @NotNull LocalDateTime offerTime
) {
}
