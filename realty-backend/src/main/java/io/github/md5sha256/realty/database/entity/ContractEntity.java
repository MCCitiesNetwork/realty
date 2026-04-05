package io.github.md5sha256.realty.database.entity;

import org.jetbrains.annotations.NotNull;

/**
 * Internal entity record mapping to the {@code Contract} DDL table.
 * <p>
 * The DDL defines a composite primary key {@code (contractId, contractType)},
 * where {@code contractId} is auto-increment and {@code contractType} is an
 * ENUM discriminator ({@code 'contract'} or {@code 'freehold'}).
 *
 * @param contractId     Auto-increment primary key component
 * @param contractType   Discriminator ENUM value ({@code "contract"} or {@code "freehold"})
 * @param realtyRegionId FK to the RealtyRegion table
 * @see io.github.md5sha256.realty.api.Contract
 */
public record ContractEntity(
        int contractId,
        @NotNull String contractType,
        int realtyRegionId
) {
}
