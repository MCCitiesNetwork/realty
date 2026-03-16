package io.github.md5sha256.realty.database.mapper;

import io.github.md5sha256.realty.database.entity.LeaseContractEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Base mapper interface for CRUD operations on the {@code LeaseContract} table.
 * SQL annotations are provided by database-specific sub-interfaces.
 *
 * @see LeaseContractEntity
 */
public interface LeaseContractMapper {

    /**
     * Inserts a new row into the {@code LeaseContract} table.
     *
     * <p>The {@code regionId} parameter identifies the {@code RealtyRegion} this lease belongs to.
     * It is not stored directly on the {@code LeaseContract} row (that association lives in the
     * {@code Contract} table), but implementations may use it for subquery-based validation or
     * linking.
     *
     * <p>{@code durationSeconds} is used rather than {@link java.time.Duration} so that the value
     * maps cleanly to the {@code durationSeconds LONG} column without a custom type-handler.
     * Convert via {@link java.time.Duration#getSeconds()} before calling.
     *
     * <p>When {@code maxRenewals} is negative the lease is treated as having unlimited renewals;
     * both {@code currentMaxExtensions} and {@code maxExtensions} are stored as {@code NULL}.
     * Otherwise {@code currentMaxExtensions} is initialised to {@code 0} and {@code maxExtensions}
     * is set to {@code maxRenewals}.
     *
     * @param regionId        the {@code realtyRegionId} of the region being leased
     * @param price           the periodic rental price (must be &gt; 0)
     * @param durationSeconds lease period in seconds (must be &gt; 0)
     * @param maxRenewals     maximum number of renewals, or negative for unlimited
     * @param tenantId        UUID of the tenant taking on the lease
     * @return number of rows inserted (1 on success)
     */
    int insertLease(int regionId,
                    double price,
                    long durationSeconds,
                    int maxRenewals,
                    @NotNull UUID tenantId);

    /**
     * Checks whether the given player is the tenant on any lease contract for the
     * specified WorldGuard region, joining through the {@code RealtyRegion} and
     * {@code Contract} tables.
     *
     * @param worldGuardRegionId the WorldGuard region identifier
     * @param worldId            UUID of the world containing the region
     * @param playerId           UUID of the player to check
     * @return {@code true} if the player is a tenant on at least one lease contract
     */
    boolean existsByRegionAndTenant(@NotNull String worldGuardRegionId,
                                    @NotNull UUID worldId,
                                    @NotNull UUID playerId);

    /**
     * Selects the lease contract associated with a WorldGuard region, joining through
     * the {@code RealtyRegion} and {@code Contract} tables.
     *
     * @param worldGuardRegionId the WorldGuard region identifier
     * @param worldId            UUID of the world containing the region
     * @return the lease contract, or {@code null} if none exists
     */
    @Nullable LeaseContractEntity selectByRegion(@NotNull String worldGuardRegionId, @NotNull UUID worldId);

}
