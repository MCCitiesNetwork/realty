package io.github.md5sha256.realty.database.maria.mapper;

import io.github.md5sha256.realty.database.entity.ExpiredLeaseView;
import io.github.md5sha256.realty.database.entity.LeaseContractEntity;
import io.github.md5sha256.realty.database.mapper.LeaseContractMapper;
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

/**
 * MariaDB-specific MyBatis mapper for CRUD operations on the {@code LeaseContract} table.
 *
 * <p>The {@code LeaseContract} table stores the tenant, rental price, period, and extension limits
 * for a rental contract. Its association with a {@code RealtyRegion} is tracked through the
 * {@code Contract} table (managed by {@link MariaContractMapper}); callers must insert the
 * corresponding {@code Contract} row <em>before</em> invoking {@link #insertLease} so that
 * referential integrity is maintained at the application level.
 *
 * @see LeaseContractEntity
 */
public interface MariaLeaseContractMapper extends LeaseContractMapper {

    /**
     * {@inheritDoc}
     *
     * <p>Inserts a single row into the {@code LeaseContract} table. The {@code regionId} parameter
     * is accepted for API consistency (and may be used by callers to look up the region) but is
     * not written to the {@code LeaseContract} table itself — that linkage is recorded in the
     * {@code Contract} table.
     *
     * <p>The {@code startDate} column is populated with {@code NOW()} by MariaDB at insert time.
     *
     * <p>When {@code maxRenewals} is negative, both {@code currentMaxExtensions} and
     * {@code maxExtensions} are stored as {@code NULL}, indicating an unlimited number of renewals.
     * Otherwise {@code currentMaxExtensions} is initialised to {@code 0} and {@code maxExtensions}
     * is set to {@code maxRenewals}.
     *
     * <p>The generated {@code leaseContractId} is set back onto the parameter map by MyBatis via
     * {@code useGeneratedKeys}.
     */
    @Override
    @Select("""
            INSERT INTO LeaseContract (landlordId, tenantId, price, durationSeconds, startDate, currentMaxExtensions, maxExtensions)
            VALUES (
                #{landlordId},
                #{tenantId},
                #{price},
                #{durationSeconds},
                NOW(),
                CASE WHEN #{maxRenewals} >= 0 THEN 0     ELSE NULL END,
                CASE WHEN #{maxRenewals} >= 0 THEN #{maxRenewals} ELSE NULL END
            )
            RETURNING leaseContractId
            """)
    int insertLease(@Param("regionId") int regionId,
                    @Param("price") double price,
                    @Param("durationSeconds") long durationSeconds,
                    @Param("maxRenewals") int maxRenewals,
                    @Param("landlordId") @NotNull UUID landlordId,
                    @Param("tenantId") @Nullable UUID tenantId);

    @Override
    @Select("""
            SELECT EXISTS (
                SELECT 1
                FROM LeaseContract lc
                INNER JOIN Contract c ON c.contractId = lc.leaseContractId AND c.contractType = 'contract'
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
            SELECT lc.leaseContractId, lc.landlordId, lc.tenantId, lc.price, lc.durationSeconds,
                   lc.startDate, lc.currentMaxExtensions, lc.maxExtensions
            FROM LeaseContract lc
            INNER JOIN Contract c ON c.contractId = lc.leaseContractId AND c.contractType = 'contract'
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = c.realtyRegionId
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            """)
    @ConstructorArgs({
            @Arg(column = "leaseContractId", javaType = int.class),
            @Arg(column = "landlordId", javaType = UUID.class),
            @Arg(column = "tenantId", javaType = UUID.class),
            @Arg(column = "price", javaType = double.class),
            @Arg(column = "durationSeconds", javaType = long.class),
            @Arg(column = "startDate", javaType = LocalDateTime.class),
            @Arg(column = "currentMaxExtensions", javaType = Integer.class),
            @Arg(column = "maxExtensions", javaType = Integer.class)
    })
    @Nullable LeaseContractEntity selectByRegion(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                                                @Param("worldId") @NotNull UUID worldId);

    @Override
    @Update("""
            UPDATE LeaseContract lc
            INNER JOIN Contract c ON c.contractId = lc.leaseContractId AND c.contractType = 'contract'
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = c.realtyRegionId
            SET lc.tenantId = #{tenantId}, lc.startDate = NOW(), lc.currentMaxExtensions = 0
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            AND lc.tenantId IS NULL
            """)
    int rentRegion(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                   @Param("worldId") @NotNull UUID worldId,
                   @Param("tenantId") @NotNull UUID tenantId);

    @Override
    @Update("""
            UPDATE LeaseContract lc
            INNER JOIN Contract c ON c.contractId = lc.leaseContractId AND c.contractType = 'contract'
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = c.realtyRegionId
            SET lc.startDate = NOW(),
                lc.currentMaxExtensions = CASE
                    WHEN lc.currentMaxExtensions IS NULL THEN NULL
                    ELSE lc.currentMaxExtensions + 1
                END
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            AND lc.tenantId = #{tenantId}
            AND (lc.currentMaxExtensions IS NULL OR lc.currentMaxExtensions < lc.maxExtensions)
            """)
    int renewLease(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                   @Param("worldId") @NotNull UUID worldId,
                   @Param("tenantId") @NotNull UUID tenantId);

    @Override
    @Select("""
            SELECT lc.leaseContractId, lc.landlordId, lc.tenantId,
                   rr.worldGuardRegionId, rr.worldId
            FROM LeaseContract lc
            INNER JOIN Contract c ON c.contractId = lc.leaseContractId AND c.contractType = 'contract'
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = c.realtyRegionId
            WHERE lc.tenantId IS NOT NULL
            AND lc.startDate + INTERVAL lc.durationSeconds SECOND < NOW()
            """)
    @ConstructorArgs({
            @Arg(column = "leaseContractId", javaType = int.class),
            @Arg(column = "landlordId", javaType = UUID.class),
            @Arg(column = "tenantId", javaType = UUID.class),
            @Arg(column = "worldGuardRegionId", javaType = String.class),
            @Arg(column = "worldId", javaType = UUID.class)
    })
    @NotNull List<ExpiredLeaseView> selectExpiredLeases();

    @Override
    @Update("""
            UPDATE LeaseContract
            SET tenantId = NULL
            WHERE leaseContractId = #{leaseContractId}
            """)
    int clearTenant(@Param("leaseContractId") int leaseContractId);

    @Override
    @Update("""
            UPDATE LeaseContract lc
            INNER JOIN Contract c ON c.contractId = lc.leaseContractId AND c.contractType = 'contract'
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
            UPDATE LeaseContract lc
            INNER JOIN Contract c ON c.contractId = lc.leaseContractId AND c.contractType = 'contract'
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = c.realtyRegionId
            SET lc.landlordId = #{landlordId}
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            """)
    int updateLandlordByRegion(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                               @Param("worldId") @NotNull UUID worldId,
                               @Param("landlordId") @NotNull UUID landlordId);

}
