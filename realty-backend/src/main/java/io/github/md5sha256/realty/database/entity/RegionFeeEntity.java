package io.github.md5sha256.realty.database.entity;

import io.github.md5sha256.realty.api.FeeSide;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Internal entity record mapping to the {@code RegionFee} DDL table: one player-set fee on a region.
 *
 * @param regionFeeId      PK
 * @param realtyRegionId   FK to {@code RealtyRegion.realtyRegionId}
 * @param recipientPartyId FK to {@code Party.partyId}; the party the fee is paid to
 * @param side             who pays the fee
 * @param percent          the fee as a percentage of the price (&gt; 0)
 * @param role             the role the fee was set under: {@code authority}, {@code titleholder} or {@code agent}
 * @param setBy            the player who set the fee
 * @param approved         whether the fee applies to sales
 * @param createdAt        when the fee was first set
 */
public record RegionFeeEntity(
        int regionFeeId,
        int realtyRegionId,
        int recipientPartyId,
        @NotNull FeeSide side,
        double percent,
        @NotNull String role,
        @NotNull UUID setBy,
        boolean approved,
        @NotNull LocalDateTime createdAt
) {
}
