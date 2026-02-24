package io.github.md5sha256.realty.database.entity;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Internal entity record mapping to the {@code SaleContract} DDL table.
 *
 * @param saleContractId Auto-increment primary key
 * @param authorityId    UUID of the authority overseeing the sale
 * @param titleHolderId  UUID of the current title holder
 * @param price          Sale price (must be &gt; 0)
 * @see io.github.md5sha256.realty.api.SaleContract
 */
public record SaleContractEntity(
        int saleContractId,
        @NotNull UUID authorityId,
        @NotNull UUID titleHolderId,
        double price
) {
}
