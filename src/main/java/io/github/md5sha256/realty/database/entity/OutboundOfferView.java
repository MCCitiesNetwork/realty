package io.github.md5sha256.realty.database.entity;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Read-only view combining an offer with its region identifier and optional payment status.
 *
 * @param worldGuardRegionId WG region ID (from RealtyRegion)
 * @param worldId            World UUID (from RealtyRegion)
 * @param offerPrice         Offered price
 * @param offerTime          When the offer was placed
 * @param currentPayment     Amount paid so far (null if not yet accepted)
 * @param paymentDeadline    Payment deadline (null if not yet accepted)
 */
public record OutboundOfferView(
        @NotNull String worldGuardRegionId,
        @NotNull UUID worldId,
        double offerPrice,
        @NotNull LocalDateTime offerTime,
        @Nullable Double currentPayment,
        @Nullable LocalDateTime paymentDeadline
) {

    public boolean accepted() {
        return paymentDeadline != null;
    }
}
