package io.github.md5sha256.realty.database.entity;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Internal entity record mapping to the {@code SaleContract} DDL table.
 *
 * @param saleContractId Auto-increment primary key
 * @param authorityId    UUID of the authority overseeing the sale
 * @param titleHolderId  UUID of the current title holder, or {@code null} if the region is for sale
 * @param price          Sale price (must be &gt; 0), or {@code null} if the region is not for sale
 * @see io.github.md5sha256.realty.api.SaleContract
 */
public record SaleContractEntity(
        int saleContractId,
        @NotNull UUID authorityId,
        @Nullable UUID titleHolderId,
        @Nullable Double price
) {
}
