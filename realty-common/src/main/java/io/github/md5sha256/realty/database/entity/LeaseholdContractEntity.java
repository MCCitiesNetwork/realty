package io.github.md5sha256.realty.database.entity;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Internal entity record mapping to the {@code LeaseholdContract} DDL table.
 *
 * @param leaseholdContractId  Auto-increment primary key
 * @param landlordId           UUID of the landlord (authority over the leasehold)
 * @param tenantId             UUID of the tenant, or {@code null} if the region is for rent
 * @param price                Rental price (must be &gt; 0)
 * @param durationSeconds      Leasehold duration in seconds (must be &gt; 0)
 * @param startDate            When the leasehold started
 * @param endDate              When the current leasehold period ends, or {@code null} if unknown
 * @param currentMaxExtensions Current extension count (nullable; must be &le; maxExtensions when present)
 * @param maxExtensions        Maximum allowed extensions (nullable)
 * @see io.github.md5sha256.realty.api.LeaseContract
 */
public record LeaseholdContractEntity(
        int leaseholdContractId,
        @NotNull UUID landlordId,
        @Nullable UUID tenantId,
        double price,
        long durationSeconds,
        @NotNull LocalDateTime startDate,
        @Nullable LocalDateTime endDate,
        @Nullable Integer currentMaxExtensions,
        @Nullable Integer maxExtensions
) {
}
