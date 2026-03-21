package io.github.md5sha256.realty.database.entity;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Internal entity record mapping to the {@code FreeholdContract} DDL table.
 *
 * @param freeholdContractId Auto-increment primary key
 * @param authorityId        UUID of the authority overseeing the freehold
 * @param titleHolderId      UUID of the current title holder, or {@code null} if the region is for freehold
 * @param price              Freehold price (must be &gt; 0), or {@code null} if the region is not for freehold
 * @see io.github.md5sha256.realty.api.FreeholdContract
 */
public record FreeholdContractEntity(
        int freeholdContractId,
        @NotNull UUID authorityId,
        @Nullable UUID titleHolderId,
        @Nullable Double price
) {
}
