package io.github.md5sha256.realty.database.entity;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Projection entity for region search results, combining region identity
 * with the contract type and price.
 *
 * @param worldGuardRegionId The WorldGuard region identifier
 * @param worldId            The world UUID
 * @param contractType       Either {@code "freehold"} or {@code "leasehold"}
 * @param price              The contract price; null for a freehold that is not
 *                           currently for sale, which only appears when the
 *                           search asked for unpriced freeholds
 * @param state              The contract's state, e.g. {@code "FOR_SALE"}
 * @param durationSeconds    The lease term a leasehold's price buys; null for a
 *                           freehold, whose price is a one-off. A rent without its
 *                           term is a number without a unit, so the projection
 *                           carries it rather than sending a listing back for it
 */
public record SearchResultEntity(
        @NotNull String worldGuardRegionId,
        @NotNull UUID worldId,
        @NotNull String contractType,
        @Nullable Double price,
        @NotNull String state,
        @Nullable Long durationSeconds
) {
}
