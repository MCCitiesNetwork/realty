package io.github.md5sha256.realty.database.mapper;

import io.github.md5sha256.realty.database.entity.ExpiredLeaseholdView;
import io.github.md5sha256.realty.database.entity.LeaseholdContractEntity;
import io.github.md5sha256.realty.database.entity.RentedRegionView;
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
                        int landlordPartyId,
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

    /** All regions rented by the given tenant, paired with each lease's end date. */
    @NotNull List<RentedRegionView> selectRentedRegionsWithEndDate(@NotNull UUID tenantId,
                                                                   int limit,
                                                                   int offset);

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

    /** Clears the tenant, guarded on the caller still being the tenant and no termination pending. */
    int unrentRegion(@NotNull String worldGuardRegionId,
                     @NotNull UUID worldId,
                     @NotNull UUID tenantId);

    /**
     * Puts a tenancy back as it was before it was ended: the same tenant, the same dates
     * and the same count of extensions used. Only on a region that has no tenant.
     */
    int restoreTenancy(@NotNull String worldGuardRegionId,
                       @NotNull UUID worldId,
                       @NotNull UUID tenantId,
                       @Nullable LocalDateTime startDate,
                       @Nullable LocalDateTime endDate,
                       @Nullable Integer extensionsUsed);

    /**
     * Puts a lease's terms back as they were before a change of terms was applied. Every
     * value is set as given, null included, where {@code applyModificationTerms} leaves
     * a term alone when given null.
     */
    int restoreTerms(@NotNull String worldGuardRegionId,
                     @NotNull UUID worldId,
                     double price,
                     long durationSeconds,
                     @Nullable Integer maxExtensions,
                     @Nullable Integer extensionsUsed);

    int rollbackRenewLeasehold(@NotNull String worldGuardRegionId,
                               @NotNull UUID worldId,
                               @NotNull UUID tenantId);

    /**
     * The next three updates take the same guards: {@code requiredLandlordPartyId}, when non-null,
     * makes the write apply only while that party is still the landlord, and {@code vacantOnly}
     * makes it apply only while there is no tenant. Pass {@code null} and {@code false} for an
     * unconditional write. They return 0 when no row matches or a guard fails.
     */
    int updateDurationByRegion(@NotNull String worldGuardRegionId,
                               @NotNull UUID worldId,
                               long durationSeconds,
                               @Nullable Integer requiredLandlordPartyId,
                               boolean vacantOnly);

    int updatePriceByRegion(@NotNull String worldGuardRegionId,
                            @NotNull UUID worldId,
                            double price,
                            @Nullable Integer requiredLandlordPartyId,
                            boolean vacantOnly);

    /** Takes the same two guards as the updates above; pass {@code null} and {@code false} for none. */
    int updateLandlordByRegion(@NotNull String worldGuardRegionId,
                               @NotNull UUID worldId,
                               int landlordPartyId,
                               @Nullable Integer requiredLandlordPartyId,
                               boolean vacantOnly);

    /** Takes the same two guards as the updates above; pass {@code null} and {@code false} for none. */
    int updateTenantByRegion(@NotNull String worldGuardRegionId,
                             @NotNull UUID worldId,
                             @Nullable UUID tenantId,
                             @Nullable Integer requiredLandlordPartyId,
                             boolean vacantOnly);

    int updateMaxRenewalsByRegion(@NotNull String worldGuardRegionId,
                                  @NotNull UUID worldId,
                                  int maxRenewals,
                                  @Nullable Integer requiredLandlordPartyId,
                                  boolean vacantOnly);

    /** Sets whether the region accepts new tenants. */
    int updateAcceptingTenantsByRegion(@NotNull String worldGuardRegionId,
                                       @NotNull UUID worldId,
                                       boolean accepting);

    /**
     * Applies non-null modification terms to the contract ({@code COALESCE} per field, so {@code null}
     * leaves a field unchanged). When a new (smaller) extension cap is applied, {@code currentMaxExtensions}
     * is clamped down so the contract's extension invariant holds; when a cap is applied to a
     * previously uncapped contract it is seeded to {@code 0}, since a {@code null} count alongside a
     * non-null cap breaks that invariant (the table's {@code chk_extensions} CHECK does not catch it,
     * as MariaDB does not evaluate CHECK constraints for multi-table UPDATE).
     */
    int applyModificationTerms(@NotNull String worldGuardRegionId,
                               @NotNull UUID worldId,
                               @Nullable Double newPrice,
                               @Nullable Long newDurationSeconds,
                               @Nullable Integer newMaxExtensions);

    int countAll();

    int countOccupied();

    int countByLandlord(int landlordPartyId);

    int countOccupiedByLandlord(int landlordPartyId);

    long averageLeaseholdDurationSeconds();

    double averagePrice();
}
