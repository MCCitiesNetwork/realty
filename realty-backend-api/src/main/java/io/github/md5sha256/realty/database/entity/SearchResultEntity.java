package io.github.md5sha256.realty.database.entity;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Projection entity for region search results, combining region identity
 * with the contract type and price.
 *
 * @param worldGuardRegionId The WorldGuard region identifier
 * @param worldId            The world UUID
 * @param contractType       Either {@code "freehold"} or {@code "leasehold"}
 * @param price              The contract price
 */
public record SearchResultEntity(
        @NotNull String worldGuardRegionId,
        @NotNull UUID worldId,
        @NotNull String contractType,
        double price
) {
}
