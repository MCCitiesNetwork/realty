package io.github.md5sha256.realty.database.entity;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Internal entity record mapping to the {@code FreeholdContractSanctionedAuctioneers} DDL table.
 *
 * @param realtyRegionId FK to the RealtyRegion table
 * @param auctioneerId   UUID of the sanctioned auctioneer
 */
public record FreeholdContractSanctionedAuctioneerEntity(
        int realtyRegionId,
        @NotNull UUID auctioneerId
) {
}
