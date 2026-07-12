package io.github.md5sha256.realty.database.entity;

import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Projection of a leasehold whose scheduled termination date has elapsed and is due to be ended by
 * the expiry sweep. Carries the fields needed to compute the prorated refund of any prepaid-but-unused
 * time (the span between {@code terminationEffectiveDate} and {@code endDate}).
 *
 * @param leaseholdContractId      Leasehold contract primary key
 * @param landlordId               Landlord (refund payer)
 * @param tenantId                 Tenant (refund recipient)
 * @param worldGuardRegionId       WorldGuard region id
 * @param worldId                  World UUID
 * @param price                    Rent price of one period
 * @param durationSeconds          Length of one period in seconds
 * @param endDate                  Paid-through end of the current period
 * @param terminationEffectiveDate When the lease actually ends
 * @param terminatedByRole         {@code "landlord"} or {@code "tenant"}
 */
public record TerminatedLeaseholdView(
        int leaseholdContractId,
        @NotNull UUID landlordId,
        @NotNull UUID tenantId,
        @NotNull String worldGuardRegionId,
        @NotNull UUID worldId,
        double price,
        long durationSeconds,
        @NotNull LocalDateTime endDate,
        @NotNull LocalDateTime terminationEffectiveDate,
        @NotNull String terminatedByRole
) {
}
