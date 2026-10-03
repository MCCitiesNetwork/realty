package io.github.md5sha256.realty.database.entity;

import io.github.md5sha256.realty.api.Party;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * Internal entity record mapping to the {@code FreeholdContract} DDL table.
 *
 * @param freeholdContractId Auto-increment primary key
 * @param authority          The authority overseeing the freehold
 * @param titleHolderId      UUID of the current title holder, or {@code null} if the region is for freehold
 * @param price              Freehold price (must be &gt; 0), or {@code null} if the region is not for freehold
 * @param acceptingOffers    Whether the region is currently accepting offers
 * @see io.github.md5sha256.realty.api.FreeholdContract
 */
public record FreeholdContractEntity(
        int freeholdContractId,
        @NotNull Party authority,
        @Nullable UUID titleHolderId,
        @Nullable Double price,
        boolean acceptingOffers
) {

    /**
     * @return the title holder as a party, if the region has one
     */
    public @NotNull Optional<Party> titleHolder() {
        return Optional.ofNullable(titleHolderId).map(Party::personal);
    }
}
