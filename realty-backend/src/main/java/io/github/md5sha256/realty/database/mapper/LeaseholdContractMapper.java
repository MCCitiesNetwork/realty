package io.github.md5sha256.realty.database.mapper;

import io.github.md5sha256.realty.database.entity.ExpiredLeaseholdView;
import io.github.md5sha256.realty.database.entity.LeaseholdContractEntity;
import io.github.md5sha256.realty.database.entity.TerminatedLeaseholdView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface LeaseholdContractMapper {

    int insertLeasehold(int regionId,
                        double price,
                        long durationSeconds,
                        int maxRenewals,
                        @NotNull UUID landlordId,
                        @Nullable UUID tenantId);

    boolean existsByRegionAndTenant(@NotNull String worldGuardRegionId,
                                    @NotNull UUID worldId,
                                    @NotNull UUID playerId);

    @Nullable LeaseholdContractEntity selectByRegion(@NotNull String worldGuardRegionId, @NotNull UUID worldId);

    int rentRegion(@NotNull String worldGuardRegionId,
                   @NotNull UUID worldId,
                   @NotNull UUID tenantId);

    int renewLeasehold(@NotNull String worldGuardRegionId,
                       @NotNull UUID worldId,
                       @NotNull UUID tenantId);

    @NotNull List<ExpiredLeaseholdView> selectExpiredLeaseholds();

    int clearTenant(int leaseholdContractId);

    /**
     * Schedules an early termination. Sets {@code endDate} to {@code newEndDate} (always &ge; the
     * effective date so the regular expiry sweep never fires first), records the effective date and
     * the initiating role. Guarded so it only applies to an occupied lease that is not already
     * terminating.
     *
     * @return rows updated (1 on success, 0 if no occupied non-terminating lease matched)
     */
    int scheduleTermination(@NotNull String worldGuardRegionId,
                            @NotNull UUID worldId,
                            @NotNull LocalDateTime newEndDate,
                            @NotNull LocalDateTime terminationEffectiveDate,
                            @NotNull String terminatedByRole);

    /** Clears a pending termination (does not touch {@code endDate}). Guarded on a termination existing. */
    int clearTermination(@NotNull String worldGuardRegionId, @NotNull UUID worldId);

    /** Leaseholds whose scheduled termination date has elapsed, due to be ended by the sweep. */
    @NotNull List<TerminatedLeaseholdView> selectTerminatedLeaseholds();

    int unrentRegion(@NotNull String worldGuardRegionId,
                     @NotNull UUID worldId,
                     @NotNull UUID tenantId);

    int rollbackRenewLeasehold(@NotNull String worldGuardRegionId,
                               @NotNull UUID worldId,
                               @NotNull UUID tenantId);

    int updateDurationByRegion(@NotNull String worldGuardRegionId,
                               @NotNull UUID worldId,
                               long durationSeconds);

    int updatePriceByRegion(@NotNull String worldGuardRegionId,
                            @NotNull UUID worldId,
                            double price);

    int updateLandlordByRegion(@NotNull String worldGuardRegionId,
                               @NotNull UUID worldId,
                               @NotNull UUID landlordId);

    int updateTenantByRegion(@NotNull String worldGuardRegionId,
                             @NotNull UUID worldId,
                             @Nullable UUID tenantId);

    int updateMaxRenewalsByRegion(@NotNull String worldGuardRegionId,
                                  @NotNull UUID worldId,
                                  int maxRenewals);

    /** Sets whether the region accepts new tenants. */
    int updateAcceptingTenantsByRegion(@NotNull String worldGuardRegionId,
                                       @NotNull UUID worldId,
                                       boolean accepting);

    /**
     * Applies non-null modification terms to the contract ({@code COALESCE} per field, so {@code null}
     * leaves a field unchanged). When a new (smaller) extension cap is applied, {@code currentMaxExtensions}
     * is clamped down so the contract's extension invariant holds.
     */
    int applyModificationTerms(@NotNull String worldGuardRegionId,
                               @NotNull UUID worldId,
                               @Nullable Double newPrice,
                               @Nullable Long newDurationSeconds,
                               @Nullable Integer newMaxExtensions);

    int countAll();

    int countOccupied();

    int countByLandlord(@NotNull UUID landlordId);

    int countOccupiedByLandlord(@NotNull UUID landlordId);

    long averageLeaseholdDurationSeconds();

    double averagePrice();
}
