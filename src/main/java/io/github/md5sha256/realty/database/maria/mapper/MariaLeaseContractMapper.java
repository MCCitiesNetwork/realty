package io.github.md5sha256.realty.database.maria.mapper;

import io.github.md5sha256.realty.database.entity.LeaseContractEntity;
import io.github.md5sha256.realty.database.mapper.LeaseContractMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.jetbrains.annotations.NotNull;

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
    @Insert("""
            INSERT INTO LeaseContract (tenantId, price, durationSeconds, startDate, currentMaxExtensions, maxExtensions)
            VALUES (
                #{tenantId},
                #{price},
                #{durationSeconds},
                NOW(),
                CASE WHEN #{maxRenewals} >= 0 THEN 0     ELSE NULL END,
                CASE WHEN #{maxRenewals} >= 0 THEN #{maxRenewals} ELSE NULL END
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "leaseContractId", keyColumn = "leaseContractId")
    int insertLease(@Param("regionId") int regionId,
                    @Param("price") double price,
                    @Param("durationSeconds") long durationSeconds,
                    @Param("maxRenewals") int maxRenewals,
                    @Param("tenantId") @NotNull UUID tenantId);

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

}
