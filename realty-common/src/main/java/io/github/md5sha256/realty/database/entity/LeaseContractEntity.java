package io.github.md5sha256.realty.database.entity;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Internal entity record mapping to the {@code LeaseContract} DDL table.
 *
 * @param leaseContractId      Auto-increment primary key
 * @param landlordId           UUID of the landlord (authority over the lease)
 * @param tenantId             UUID of the tenant, or {@code null} if the region is for rent
 * @param price                Rental price (must be &gt; 0)
 * @param durationSeconds      Lease duration in seconds (must be &gt; 0)
 * @param startDate            When the lease started
 * @param currentMaxExtensions Current extension count (nullable; must be &le; maxExtensions when present)
 * @param maxExtensions        Maximum allowed extensions (nullable)
 * @see io.github.md5sha256.realty.api.LeaseContract
 */
public record LeaseContractEntity(
        int leaseContractId,
        @NotNull UUID landlordId,
        @Nullable UUID tenantId,
        double price,
        long durationSeconds,
        @NotNull LocalDateTime startDate,
        @Nullable Integer currentMaxExtensions,
        @Nullable Integer maxExtensions
) {
}
