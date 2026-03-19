package io.github.md5sha256.realty.database.entity;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Read-only view of an offer received on a region where the querying player is the authority.
 *
 * @param worldGuardRegionId WG region ID (from RealtyRegion)
 * @param worldId            World UUID (from RealtyRegion)
 * @param offererId          UUID of the player who placed the offer
 * @param offerPrice         Offered price
 * @param offerTime          When the offer was placed
 * @param currentPayment     Amount paid so far (null if not yet accepted)
 * @param paymentDeadline    Payment deadline (null if not yet accepted)
 */
public record InboundOfferView(
        @NotNull String worldGuardRegionId,
        @NotNull UUID worldId,
        @NotNull UUID offererId,
        double offerPrice,
        @NotNull LocalDateTime offerTime,
        @Nullable Double currentPayment,
        @Nullable LocalDateTime paymentDeadline
) {

    public boolean accepted() {
        return paymentDeadline != null;
    }
}
