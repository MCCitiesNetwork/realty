package io.github.md5sha256.realty.database.maria.mapper;

import io.github.md5sha256.realty.database.entity.ExpiredLeaseholdView;
import io.github.md5sha256.realty.database.entity.LeaseholdContractEntity;
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
            INSERT INTO LeaseholdContract (landlordId, tenantId, price, durationSeconds, startDate, endDate, currentMaxExtensions, maxExtensions)
            VALUES (
                #{landlordId},
                #{tenantId},
                #{price},
                #{durationSeconds},
                NOW(),
                NOW() + INTERVAL #{durationSeconds} SECOND,
                CASE WHEN #{maxRenewals} >= 0 THEN 0     ELSE NULL END,
                CASE WHEN #{maxRenewals} >= 0 THEN #{maxRenewals} ELSE NULL END
            )
            RETURNING leaseholdContractId
            """)
    int insertLeasehold(@Param("regionId") int regionId,
                        @Param("price") double price,
                        @Param("durationSeconds") long durationSeconds,
                        @Param("maxRenewals") int maxRenewals,
                        @Param("landlordId") @NotNull UUID landlordId,
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
            SELECT lc.leaseholdContractId, lc.landlordId, lc.tenantId, lc.price, lc.durationSeconds,
                   lc.startDate, lc.endDate, lc.currentMaxExtensions, lc.maxExtensions
            FROM LeaseholdContract lc
            INNER JOIN Contract c ON c.contractId = lc.leaseholdContractId AND c.contractType = 'leasehold'
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = c.realtyRegionId
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            """)
    @ConstructorArgs({
            @Arg(column = "leaseholdContractId", javaType = int.class),
            @Arg(column = "landlordId", javaType = UUID.class),
            @Arg(column = "tenantId", javaType = UUID.class),
            @Arg(column = "price", javaType = double.class),
            @Arg(column = "durationSeconds", javaType = long.class),
            @Arg(column = "startDate", javaType = LocalDateTime.class),
            @Arg(column = "endDate", javaType = LocalDateTime.class),
            @Arg(column = "currentMaxExtensions", javaType = Integer.class),
            @Arg(column = "maxExtensions", javaType = Integer.class)
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
            """)
    int rentRegion(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                   @Param("worldId") @NotNull UUID worldId,
                   @Param("tenantId") @NotNull UUID tenantId);

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
    @Select("""
            SELECT lc.leaseholdContractId, lc.landlordId, lc.tenantId,
                   rr.worldGuardRegionId, rr.worldId
            FROM LeaseholdContract lc
            INNER JOIN Contract c ON c.contractId = lc.leaseholdContractId AND c.contractType = 'leasehold'
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = c.realtyRegionId
            WHERE lc.tenantId IS NOT NULL
            AND lc.endDate < NOW()
            """)
    @ConstructorArgs({
            @Arg(column = "leaseholdContractId", javaType = int.class),
            @Arg(column = "landlordId", javaType = UUID.class),
            @Arg(column = "tenantId", javaType = UUID.class),
            @Arg(column = "worldGuardRegionId", javaType = String.class),
            @Arg(column = "worldId", javaType = UUID.class)
    })
    @NotNull List<ExpiredLeaseholdView> selectExpiredLeaseholds();

    @Override
    @Update("""
            UPDATE LeaseholdContract
            SET tenantId = NULL,
                startDate = NULL,
                endDate = NULL,
                currentMaxExtensions = CASE WHEN maxExtensions IS NOT NULL THEN 0 ELSE NULL END
            WHERE leaseholdContractId = #{leaseholdContractId}
            """)
    int clearTenant(@Param("leaseholdContractId") int leaseholdContractId);

    @Override
    @Update("""
            UPDATE LeaseholdContract lc
            INNER JOIN Contract c ON c.contractId = lc.leaseholdContractId AND c.contractType = 'leasehold'
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = c.realtyRegionId
            SET lc.durationSeconds = #{durationSeconds}
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            """)
    int updateDurationByRegion(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                               @Param("worldId") @NotNull UUID worldId,
                               @Param("durationSeconds") long durationSeconds);

    @Override
    @Update("""
            UPDATE LeaseholdContract lc
            INNER JOIN Contract c ON c.contractId = lc.leaseholdContractId AND c.contractType = 'leasehold'
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = c.realtyRegionId
            SET lc.price = #{price}
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            """)
    int updatePriceByRegion(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                            @Param("worldId") @NotNull UUID worldId,
                            @Param("price") double price);

    @Override
    @Update("""
            UPDATE LeaseholdContract lc
            INNER JOIN Contract c ON c.contractId = lc.leaseholdContractId AND c.contractType = 'leasehold'
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = c.realtyRegionId
            SET lc.landlordId = #{landlordId}
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            """)
    int updateLandlordByRegion(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                               @Param("worldId") @NotNull UUID worldId,
                               @Param("landlordId") @NotNull UUID landlordId);

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
            """)
    int updateTenantByRegion(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                             @Param("worldId") @NotNull UUID worldId,
                             @Param("tenantId") @Nullable UUID tenantId);

    @Override
    @Update("""
            UPDATE LeaseholdContract lc
            INNER JOIN Contract c ON c.contractId = lc.leaseholdContractId AND c.contractType = 'leasehold'
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = c.realtyRegionId
            SET lc.maxExtensions = CASE WHEN #{maxRenewals} >= 0 THEN #{maxRenewals} ELSE NULL END,
                lc.currentMaxExtensions = CASE WHEN #{maxRenewals} >= 0 THEN 0 ELSE NULL END
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            """)
    int updateMaxRenewalsByRegion(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                                  @Param("worldId") @NotNull UUID worldId,
                                  @Param("maxRenewals") int maxRenewals);

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
            WHERE landlordId = #{landlordId}
            """)
    int countByLandlord(@Param("landlordId") @NotNull UUID landlordId);

    @Override
    @Select("""
            SELECT COUNT(*)
            FROM LeaseholdContract
            WHERE landlordId = #{landlordId}
            AND tenantId IS NOT NULL
            """)
    int countOccupiedByLandlord(@Param("landlordId") @NotNull UUID landlordId);

    @Override
    @Select("""
            SELECT COALESCE(AVG(TIMESTAMPDIFF(SECOND, startDate, endDate)), 0)
            FROM LeaseholdContract
            WHERE tenantId IS NOT NULL
            AND startDate IS NOT NULL
            """)
    long averageLeaseholdDurationSeconds();
}
