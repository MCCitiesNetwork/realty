package io.github.md5sha256.realty.database.entity;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Projection entity pairing a rented region with its leasehold end date, so a
 * caller listing a tenant's rented regions does not need one lookup per region.
 *
 * @param worldGuardRegionId The WorldGuard region identifier
 * @param worldId            The world UUID
 * @param endDate            When the lease ends, or {@code null} for a lease with no end date
 */
public record RentedRegionView(
        @NotNull String worldGuardRegionId,
        @NotNull UUID worldId,
        @Nullable LocalDateTime endDate
) {
}
