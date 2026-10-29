package io.github.md5sha256.realty.database.entity;

import io.github.md5sha256.realty.api.Party;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** A freehold that accepts offers or is being paid for through one, before its offers are added. */
public record OfferListingRow(
        @NotNull String worldGuardRegionId,
        @NotNull UUID worldId,
        @NotNull Party authority,
        @Nullable UUID titleHolderId,
        @Nullable Double price
) {
}
