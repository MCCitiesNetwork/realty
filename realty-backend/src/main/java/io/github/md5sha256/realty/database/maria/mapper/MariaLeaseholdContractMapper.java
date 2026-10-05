package io.github.md5sha256.realty.database.maria.mapper;

import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.database.entity.ExpiredLeaseholdView;
import io.github.md5sha256.realty.database.entity.LeaseholdContractEntity;
import io.github.md5sha256.realty.database.entity.RentedRegionView;
import io.github.md5sha256.realty.database.entity.TerminatedLeaseholdView;
import io.github.md5sha256.realty.database.mapper.LeaseholdContractMapper;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface MariaLeaseholdContractMapper extends LeaseholdContractMapper {

    @Override
    @Select("""
            INSERT INTO LeaseholdContract (landlordPartyId, tenantId, price, durationSeconds, startDate, endDate, currentMaxExtensions, maxExtensions)
            VALUES (
                #{landlordPartyId},
                #{tenantId},
                #{price},
                #{durationSeconds},
                CASE WHEN #{tenantId} IS NULL THEN NULL ELSE NOW() END,
                CASE WHEN #{tenantId} IS NULL THEN NULL ELSE NOW() + INTERVAL #{durationSeconds} SECOND END,
                CASE WHEN #{maxRenewals} >= 0 THEN 0     ELSE NULL END,
                CASE WHEN #{maxRenewals} >= 0 THEN #{maxRenewals} ELSE NULL END
            )
            RETURNING leaseholdContractId
            """)
    int insertLeasehold(@Param("regionId") int regionId,
                        @Param("price") double price,
                        @Param("durationSeconds") long durationSeconds,
                        @Param("maxRenewals") int maxRenewals,
                        @Param("landlordPartyId") int landlordPartyId,
                        @Param("tenantId") @Nullable UUID tenantId);

    @Override
    @Select("""
            SELECT EXISTS (
                SELECT 1
                FROM LeaseholdContract lc
                INNER JOIN Contract c ON c.contractId = lc.leaseholdContractId AND c.contractType = 'leasehold'
                INNER JOIN RealtyRegion rr ON rr.realtyRegionId = c.realtyRegionId
                WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
                AND rr.worldId = #{worldId}
                AND lc.tenantId = #{playerId}
            )
            """)
    boolean existsByRegionAndTenant(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                                    @Param("worldId") @NotNull UUID worldId,
                                    @Param("playerId") @NotNull UUID playerId);

    @Override
    @Select("""
            SELECT lc.leaseholdContractId, lc.tenantId, lc.price, lc.durationSeconds,
                   lc.startDate, lc.endDate, lc.currentMaxExtensions, lc.maxExtensions,
                   lc.terminationEffectiveDate, lc.terminatedByRole, lc.acceptingTenants,
            """ + PartySql.LANDLORD_COLUMNS + """
            FROM LeaseholdContract lc
            INNER JOIN Contract c ON c.contractId = lc.leaseholdContractId AND c.contractType = 'leasehold'
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = c.realtyRegionId
            """ + PartySql.LANDLORD_JOINS_CONTRACT + """
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            """)
    @ConstructorArgs({
            @Arg(column = "leaseholdContractId", javaType = int.class),
            @Arg(resultMap = PartySql.RESULT_MAP, columnPrefix = "landlord_", javaType = Party.class),
            @Arg(column = "tenantId", javaType = UUID.class),
            @Arg(column = "price", javaType = double.class),
            @Arg(column = "durationSeconds", javaType = long.class),
            @Arg(column = "startDate", javaType = LocalDateTime.class),
            @Arg(column = "endDate", javaType = LocalDateTime.class),
            @Arg(column = "currentMaxExtensions", javaType = Integer.class),
            @Arg(column = "maxExtensions", javaType = Integer.class),
            @Arg(column = "terminationEffectiveDate", javaType = LocalDateTime.class),
            @Arg(column = "terminatedByRole", javaType = String.class),
            @Arg(column = "acceptingTenants", javaType = boolean.class)
    })
    @Nullable LeaseholdContractEntity selectByRegion(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                                                     @Param("worldId") @NotNull UUID worldId);

    @Override
    @Update("""
            UPDATE LeaseholdContract lc
            INNER JOIN Contract c ON c.contractId = lc.leaseholdContractId AND c.contractType = 'leasehold'
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = c.realtyRegionId
            SET lc.tenantId = #{tenantId}, lc.startDate = NOW(),
                lc.endDate = NOW() + INTERVAL lc.durationSeconds SECOND,
                lc.currentMaxExtensions = CASE WHEN lc.maxExtensions IS NOT NULL THEN 0 ELSE NULL END
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            AND lc.tenantId IS NULL
            AND lc.acceptingTenants = TRUE
            """)
    int rentRegion(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                   @Param("worldId") @NotNull UUID worldId,
                   @Param("tenantId") @NotNull UUID tenantId);

    @Override
    @Update("""
            UPDATE LeaseholdContract lc
            INNER JOIN Contract c ON c.contractId = lc.leaseholdContractId AND c.contractType = 'leasehold'
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = c.realtyRegionId
            SET lc.acceptingTenants = #{accepting}
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            """)
    int updateAcceptingTenantsByRegion(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                                       @Param("worldId") @NotNull UUID worldId,
                                       @Param("accepting") boolean accepting);

    @Override
    @Update("""
            UPDATE LeaseholdContract lc
            INNER JOIN Contract c ON c.contractId = lc.leaseholdContractId AND c.contractType = 'leasehold'
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = c.realtyRegionId
            SET lc.endDate = lc.endDate + INTERVAL lc.durationSeconds SECOND,
                lc.currentMaxExtensions = CASE
                    WHEN lc.currentMaxExtensions IS NULL THEN NULL
                    ELSE lc.currentMaxExtensions + 1
                END
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            AND lc.tenantId = #{tenantId}
            AND (lc.currentMaxExtensions IS NULL OR lc.currentMaxExtensions < lc.maxExtensions)
            """)
    int renewLeasehold(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                       @Param("worldId") @NotNull UUID worldId,
                       @Param("tenantId") @NotNull UUID tenantId);

    @Override
    // The count is held to the cap as it is now. A landlord may have lowered the cap
    // since the tenancy was ended, and this statement is not checked against it.
    @Update("""
            UPDATE LeaseholdContract lc
            INNER JOIN Contract c ON c.contractId = lc.leaseholdContractId AND c.contractType = 'leasehold'
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = c.realtyRegionId
            SET lc.tenantId = #{tenantId},
                lc.startDate = #{startDate},
                lc.endDate = #{endDate},
                lc.currentMaxExtensions = CASE
                    WHEN lc.maxExtensions IS NULL THEN NULL
                    ELSE LEAST(COALESCE(#{extensionsUsed}, 0), lc.maxExtensions)
                END
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            AND lc.tenantId IS NULL
            """)
    int restoreTenancy(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                       @Param("worldId") @NotNull UUID worldId,
                       @Param("tenantId") @NotNull UUID tenantId,
                       @Param("startDate") @Nullable LocalDateTime startDate,
                       @Param("endDate") @Nullable LocalDateTime endDate,
                       @Param("extensionsUsed") @Nullable Integer extensionsUsed);

    @Override
    @Update("""
            UPDATE LeaseholdContract lc
            INNER JOIN Contract c ON c.contractId = lc.leaseholdContractId AND c.contractType = 'leasehold'
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = c.realtyRegionId
            SET lc.price = #{price},
                lc.durationSeconds = #{durationSeconds},
                lc.maxExtensions = #{maxExtensions},
                lc.currentMaxExtensions = #{extensionsUsed}
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            """)
    int restoreTerms(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                     @Param("worldId") @NotNull UUID worldId,
                     @Param("price") double price,
                     @Param("durationSeconds") long durationSeconds,
                     @Param("maxExtensions") @Nullable Integer maxExtensions,
                     @Param("extensionsUsed") @Nullable Integer extensionsUsed);

    @Override
    @Update("""
            UPDATE LeaseholdContract lc
            INNER JOIN Contract c ON c.contractId = lc.leaseholdContractId AND c.contractType = 'leasehold'
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = c.realtyRegionId
            SET lc.endDate = lc.endDate - INTERVAL lc.durationSeconds SECOND,
                lc.currentMaxExtensions = CASE
                    WHEN lc.currentMaxExtensions IS NULL THEN NULL
                    ELSE GREATEST(0, lc.currentMaxExtensions - 1)
                END
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            AND lc.tenantId = #{tenantId}
            """)
    int rollbackRenewLeasehold(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                               @Param("worldId") @NotNull UUID worldId,
                               @Param("tenantId") @NotNull UUID tenantId);

    @Override
    @Select("""
            SELECT lc.leaseholdContractId, lc.tenantId,
                   rr.worldGuardRegionId, rr.worldId,
            """ + PartySql.LANDLORD_COLUMNS + """
            FROM LeaseholdContract lc
            INNER JOIN Contract c ON c.contractId = lc.leaseholdContractId AND c.contractType = 'leasehold'
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = c.realtyRegionId
            """ + PartySql.LANDLORD_JOINS_CONTRACT + """
            WHERE lc.tenantId IS NOT NULL
            AND lc.endDate < NOW()
            """)
    @ConstructorArgs({
            @Arg(column = "leaseholdContractId", javaType = int.class),
            @Arg(resultMap = PartySql.RESULT_MAP, columnPrefix = "landlord_", javaType = Party.class),
            @Arg(column = "tenantId", javaType = UUID.class),
            @Arg(column = "worldGuardRegionId", javaType = String.class),
            @Arg(column = "worldId", javaType = UUID.class)
    })
    @NotNull List<ExpiredLeaseholdView> selectExpiredLeaseholds();

    @Override
    @Select("""
            SELECT rr.worldGuardRegionId, rr.worldId, lc.endDate
            FROM LeaseholdContract lc
            INNER JOIN Contract c ON c.contractId = lc.leaseholdContractId AND c.contractType = 'leasehold'
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = c.realtyRegionId
            WHERE lc.tenantId = #{tenantId}
            ORDER BY rr.worldGuardRegionId, rr.worldId, rr.realtyRegionId
            LIMIT #{limit} OFFSET #{offset}
            """)
    @ConstructorArgs({
            @Arg(column = "worldGuardRegionId", javaType = String.class),
            @Arg(column = "worldId", javaType = UUID.class),
            @Arg(column = "endDate", javaType = LocalDateTime.class)
    })
    @NotNull List<RentedRegionView> selectRentedRegionsWithEndDate(
            @Param("tenantId") @NotNull UUID tenantId,
            @Param("limit") int limit,
            @Param("offset") int offset);

    @Override
    @Update("""
            UPDATE LeaseholdContract
            SET tenantId = NULL,
                startDate = NULL,
                endDate = NULL,
                terminationEffectiveDate = NULL,
                terminatedByRole = NULL,
                currentMaxExtensions = CASE WHEN maxExtensions IS NOT NULL THEN 0 ELSE NULL END
            WHERE leaseholdContractId = #{leaseholdContractId}
            """)
    int clearTenant(@Param("leaseholdContractId") int leaseholdContractId);

    @Override
    @Update("""
            UPDATE LeaseholdContract lc
            INNER JOIN Contract c ON c.contractId = lc.leaseholdContractId AND c.contractType = 'leasehold'
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = c.realtyRegionId
            SET lc.endDate = #{newEndDate},
                lc.terminationEffectiveDate = #{terminationEffectiveDate},
                lc.terminatedByRole = #{terminatedByRole}
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            AND lc.tenantId IS NOT NULL
            AND lc.terminationEffectiveDate IS NULL
            """)
    int scheduleTermination(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                            @Param("worldId") @NotNull UUID worldId,
                            @Param("newEndDate") @NotNull LocalDateTime newEndDate,
                            @Param("terminationEffectiveDate") @NotNull LocalDateTime terminationEffectiveDate,
                            @Param("terminatedByRole") @NotNull String terminatedByRole);

    @Override
    @Update("""
            UPDATE LeaseholdContract lc
            INNER JOIN Contract c ON c.contractId = lc.leaseholdContractId AND c.contractType = 'leasehold'
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = c.realtyRegionId
            SET lc.terminationEffectiveDate = NULL,
                lc.terminatedByRole = NULL
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            AND lc.terminationEffectiveDate IS NOT NULL
            """)
    int clearTermination(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                         @Param("worldId") @NotNull UUID worldId);

    @Override
    @Select("""
            SELECT lc.leaseholdContractId, lc.tenantId,
                   rr.worldGuardRegionId, rr.worldId,
                   lc.price, lc.durationSeconds, lc.endDate,
                   lc.terminationEffectiveDate, lc.terminatedByRole,
            """ + PartySql.LANDLORD_COLUMNS + """
            FROM LeaseholdContract lc
            INNER JOIN Contract c ON c.contractId = lc.leaseholdContractId AND c.contractType = 'leasehold'
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = c.realtyRegionId
            """ + PartySql.LANDLORD_JOINS_CONTRACT + """
            WHERE lc.tenantId IS NOT NULL
            AND lc.terminationEffectiveDate IS NOT NULL
            AND lc.terminationEffectiveDate <= NOW()
            """)
    @ConstructorArgs({
            @Arg(column = "leaseholdContractId", javaType = int.class),
            @Arg(resultMap = PartySql.RESULT_MAP, columnPrefix = "landlord_", javaType = Party.class),
            @Arg(column = "tenantId", javaType = UUID.class),
            @Arg(column = "worldGuardRegionId", javaType = String.class),
            @Arg(column = "worldId", javaType = UUID.class),
            @Arg(column = "price", javaType = double.class),
            @Arg(column = "durationSeconds", javaType = long.class),
            @Arg(column = "endDate", javaType = LocalDateTime.class),
            @Arg(column = "terminationEffectiveDate", javaType = LocalDateTime.class),
            @Arg(column = "terminatedByRole", javaType = String.class)
    })
    @NotNull List<TerminatedLeaseholdView> selectTerminatedLeaseholds();

    @Override
    @Update("""
            UPDATE LeaseholdContract lc
            INNER JOIN Contract c ON c.contractId = lc.leaseholdContractId AND c.contractType = 'leasehold'
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = c.realtyRegionId
            SET lc.tenantId = NULL,
                lc.startDate = NULL,
                lc.endDate = NULL,
                lc.terminationEffectiveDate = NULL,
                lc.terminatedByRole = NULL,
                lc.currentMaxExtensions = CASE WHEN lc.maxExtensions IS NOT NULL THEN 0 ELSE NULL END
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            AND lc.tenantId = #{tenantId}
            AND lc.terminationEffectiveDate IS NULL
            """)
    int unrentRegion(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                     @Param("worldId") @NotNull UUID worldId,
                     @Param("tenantId") @NotNull UUID tenantId);

    @Override
    @Update("""
            UPDATE LeaseholdContract lc
            INNER JOIN Contract c ON c.contractId = lc.leaseholdContractId AND c.contractType = 'leasehold'
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = c.realtyRegionId
            SET lc.durationSeconds = #{durationSeconds}
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            AND (#{requiredLandlordPartyId} IS NULL OR lc.landlordPartyId = #{requiredLandlordPartyId})
            AND (#{vacantOnly} = FALSE OR lc.tenantId IS NULL)
            """)
    int updateDurationByRegion(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                               @Param("worldId") @NotNull UUID worldId,
                               @Param("durationSeconds") long durationSeconds,
                               @Param("requiredLandlordPartyId") @Nullable Integer requiredLandlordPartyId,
                               @Param("vacantOnly") boolean vacantOnly);

    @Override
    @Update("""
            UPDATE LeaseholdContract lc
            INNER JOIN Contract c ON c.contractId = lc.leaseholdContractId AND c.contractType = 'leasehold'
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = c.realtyRegionId
            SET lc.price = #{price}
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            AND (#{requiredLandlordPartyId} IS NULL OR lc.landlordPartyId = #{requiredLandlordPartyId})
            AND (#{vacantOnly} = FALSE OR lc.tenantId IS NULL)
            """)
    int updatePriceByRegion(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                            @Param("worldId") @NotNull UUID worldId,
                            @Param("price") double price,
                            @Param("requiredLandlordPartyId") @Nullable Integer requiredLandlordPartyId,
                            @Param("vacantOnly") boolean vacantOnly);

    @Override
    @Update("""
            UPDATE LeaseholdContract lc
            INNER JOIN Contract c ON c.contractId = lc.leaseholdContractId AND c.contractType = 'leasehold'
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = c.realtyRegionId
            SET lc.landlordPartyId = #{landlordPartyId}
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            AND (#{requiredLandlordPartyId} IS NULL OR lc.landlordPartyId = #{requiredLandlordPartyId})
            AND (#{vacantOnly} = FALSE OR lc.tenantId IS NULL)
            """)
    int updateLandlordByRegion(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                               @Param("worldId") @NotNull UUID worldId,
                               @Param("landlordPartyId") int landlordPartyId,
                               @Param("requiredLandlordPartyId") @Nullable Integer requiredLandlordPartyId,
                               @Param("vacantOnly") boolean vacantOnly);

    @Override
    @Update("""
            UPDATE LeaseholdContract lc
            INNER JOIN Contract c ON c.contractId = lc.leaseholdContractId AND c.contractType = 'leasehold'
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = c.realtyRegionId
            SET lc.tenantId = #{tenantId},
                lc.startDate = CASE WHEN #{tenantId} IS NULL THEN NULL ELSE lc.startDate END,
                lc.endDate = CASE WHEN #{tenantId} IS NULL THEN NULL ELSE lc.endDate END,
                lc.currentMaxExtensions = CASE
                    WHEN #{tenantId} IS NULL AND lc.maxExtensions IS NOT NULL THEN 0
                    ELSE lc.currentMaxExtensions
                END
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            AND (#{requiredLandlordPartyId} IS NULL OR lc.landlordPartyId = #{requiredLandlordPartyId})
            AND (#{vacantOnly} = FALSE OR lc.tenantId IS NULL)
            """)
    int updateTenantByRegion(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                             @Param("worldId") @NotNull UUID worldId,
                             @Param("tenantId") @Nullable UUID tenantId,
                             @Param("requiredLandlordPartyId") @Nullable Integer requiredLandlordPartyId,
                             @Param("vacantOnly") boolean vacantOnly);

    @Override
    @Update("""
            UPDATE LeaseholdContract lc
            INNER JOIN Contract c ON c.contractId = lc.leaseholdContractId AND c.contractType = 'leasehold'
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = c.realtyRegionId
            SET lc.maxExtensions = CASE WHEN #{maxRenewals} >= 0 THEN #{maxRenewals} ELSE NULL END,
                lc.currentMaxExtensions = CASE WHEN #{maxRenewals} >= 0 THEN 0 ELSE NULL END
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            AND (#{requiredLandlordPartyId} IS NULL OR lc.landlordPartyId = #{requiredLandlordPartyId})
            AND (#{vacantOnly} = FALSE OR lc.tenantId IS NULL)
            """)
    int updateMaxRenewalsByRegion(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                                  @Param("worldId") @NotNull UUID worldId,
                                  @Param("maxRenewals") int maxRenewals,
                                  @Param("requiredLandlordPartyId") @Nullable Integer requiredLandlordPartyId,
                                  @Param("vacantOnly") boolean vacantOnly);

    @Override
    @Update("""
            UPDATE LeaseholdContract lc
            INNER JOIN Contract c ON c.contractId = lc.leaseholdContractId AND c.contractType = 'leasehold'
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = c.realtyRegionId
            SET lc.price = COALESCE(#{newPrice}, lc.price),
                lc.durationSeconds = COALESCE(#{newDurationSeconds}, lc.durationSeconds),
                lc.maxExtensions = COALESCE(#{newMaxExtensions}, lc.maxExtensions),
                lc.currentMaxExtensions = CASE
                    WHEN #{newMaxExtensions} IS NULL THEN lc.currentMaxExtensions
                    WHEN lc.currentMaxExtensions IS NULL THEN 0
                    ELSE LEAST(lc.currentMaxExtensions, #{newMaxExtensions})
                END
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            """)
    int applyModificationTerms(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                               @Param("worldId") @NotNull UUID worldId,
                               @Param("newPrice") @Nullable Double newPrice,
                               @Param("newDurationSeconds") @Nullable Long newDurationSeconds,
                               @Param("newMaxExtensions") @Nullable Integer newMaxExtensions);

    @Override
    @Select("""
            SELECT COUNT(*)
            FROM LeaseholdContract
            """)
    int countAll();

    @Override
    @Select("""
            SELECT COUNT(*)
            FROM LeaseholdContract
            WHERE tenantId IS NOT NULL
            """)
    int countOccupied();

    @Override
    @Select("""
            SELECT COUNT(*)
            FROM LeaseholdContract
            WHERE landlordPartyId = #{landlordPartyId}
            """)
    int countByLandlord(@Param("landlordPartyId") int landlordPartyId);

    @Override
    @Select("""
            SELECT COUNT(*)
            FROM LeaseholdContract
            WHERE landlordPartyId = #{landlordPartyId}
            AND tenantId IS NOT NULL
            """)
    int countOccupiedByLandlord(@Param("landlordPartyId") int landlordPartyId);

    @Override
    @Select("""
            SELECT COALESCE(AVG(TIMESTAMPDIFF(SECOND, startDate, endDate)), 0)
            FROM LeaseholdContract
            WHERE tenantId IS NOT NULL
            AND startDate IS NOT NULL
            """)
    long averageLeaseholdDurationSeconds();

    @Override
    @Select("""
            SELECT COALESCE(AVG(price), 0)
            FROM LeaseholdContract
            """)
    double averagePrice();
}
