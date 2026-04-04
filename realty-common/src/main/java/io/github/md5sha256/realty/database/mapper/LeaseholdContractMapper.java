package io.github.md5sha256.realty.database.mapper;

import io.github.md5sha256.realty.database.entity.ExpiredLeaseholdView;
import io.github.md5sha256.realty.database.entity.LeaseholdContractEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

    int countAll();

    int countOccupied();

    int countByLandlord(@NotNull UUID landlordId);

    int countOccupiedByLandlord(@NotNull UUID landlordId);

    long averageLeaseholdDurationSeconds();

    double averagePrice();
}
