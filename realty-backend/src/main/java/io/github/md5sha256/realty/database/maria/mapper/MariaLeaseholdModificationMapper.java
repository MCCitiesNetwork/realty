package io.github.md5sha256.realty.database.maria.mapper;

import io.github.md5sha256.realty.database.entity.LeaseholdModificationEntity;
import io.github.md5sha256.realty.database.entity.LeaseholdModificationView;
import io.github.md5sha256.realty.database.mapper.LeaseholdModificationMapper;
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

public interface MariaLeaseholdModificationMapper extends LeaseholdModificationMapper {

    @Override
    @Select("""
            INSERT INTO LeaseholdModification
                (leaseholdContractId, proposerRole, proposerId, newPrice, newDurationSeconds, newMaxExtensions, status)
            VALUES (#{leaseholdContractId}, #{proposerRole}, #{proposerId},
                    #{newPrice}, #{newDurationSeconds}, #{newMaxExtensions}, #{status})
            RETURNING modificationId
            """)
    int insert(@Param("leaseholdContractId") int leaseholdContractId,
               @Param("proposerRole") @NotNull String proposerRole,
               @Param("proposerId") @NotNull UUID proposerId,
               @Param("newPrice") @Nullable Double newPrice,
               @Param("newDurationSeconds") @Nullable Long newDurationSeconds,
               @Param("newMaxExtensions") @Nullable Integer newMaxExtensions,
               @Param("status") @NotNull String status);

    @Override
    @Select("""
            SELECT modificationId, leaseholdContractId, proposerRole, proposerId,
                   newPrice, newDurationSeconds, newMaxExtensions, status, createdAt, resolvedAt
            FROM LeaseholdModification
            WHERE leaseholdContractId = #{leaseholdContractId}
            AND status IN ('AWAITING_LANDLORD', 'ACTIVE')
            ORDER BY modificationId DESC
            LIMIT 1
            """)
    @ConstructorArgs({
            @Arg(column = "modificationId", javaType = int.class),
            @Arg(column = "leaseholdContractId", javaType = int.class),
            @Arg(column = "proposerRole", javaType = String.class),
            @Arg(column = "proposerId", javaType = UUID.class),
            @Arg(column = "newPrice", javaType = Double.class),
            @Arg(column = "newDurationSeconds", javaType = Long.class),
            @Arg(column = "newMaxExtensions", javaType = Integer.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "createdAt", javaType = LocalDateTime.class),
            @Arg(column = "resolvedAt", javaType = LocalDateTime.class)
    })
    @Nullable LeaseholdModificationEntity selectActiveByContract(
            @Param("leaseholdContractId") int leaseholdContractId);

    @Override
    @Select("""
            SELECT m.modificationId, m.leaseholdContractId, m.proposerRole, m.proposerId,
                   m.newPrice, m.newDurationSeconds, m.newMaxExtensions, m.status, m.createdAt, m.resolvedAt
            FROM LeaseholdModification m
            INNER JOIN Contract c ON c.contractId = m.leaseholdContractId AND c.contractType = 'leasehold'
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = c.realtyRegionId
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            AND m.status IN ('AWAITING_LANDLORD', 'ACTIVE')
            ORDER BY m.modificationId DESC
            LIMIT 1
            """)
    @ConstructorArgs({
            @Arg(column = "modificationId", javaType = int.class),
            @Arg(column = "leaseholdContractId", javaType = int.class),
            @Arg(column = "proposerRole", javaType = String.class),
            @Arg(column = "proposerId", javaType = UUID.class),
            @Arg(column = "newPrice", javaType = Double.class),
            @Arg(column = "newDurationSeconds", javaType = Long.class),
            @Arg(column = "newMaxExtensions", javaType = Integer.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "createdAt", javaType = LocalDateTime.class),
            @Arg(column = "resolvedAt", javaType = LocalDateTime.class)
    })
    @Nullable LeaseholdModificationEntity selectActiveByRegion(
            @Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
            @Param("worldId") @NotNull UUID worldId);

    @Override
    @Update("""
            UPDATE LeaseholdModification
            SET status = #{status},
                resolvedAt = CASE WHEN #{status} IN ('APPLIED', 'REJECTED', 'WITHDRAWN', 'SUPERSEDED')
                                  THEN NOW() ELSE resolvedAt END
            WHERE modificationId = #{modificationId}
            """)
    int updateStatus(@Param("modificationId") int modificationId,
                     @Param("status") @NotNull String status);

    @Override
    @Select("""
            SELECT rr.worldGuardRegionId, rr.worldId, m.proposerRole, m.proposerId,
                   m.newPrice, m.newDurationSeconds, m.newMaxExtensions, m.status, m.createdAt
            FROM LeaseholdModification m
            INNER JOIN Contract c ON c.contractId = m.leaseholdContractId AND c.contractType = 'leasehold'
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = c.realtyRegionId
            INNER JOIN LeaseholdContract lc ON lc.leaseholdContractId = m.leaseholdContractId
            WHERE m.status = 'AWAITING_LANDLORD'
            AND lc.landlordId = #{landlordId}
            ORDER BY m.createdAt DESC
            """)
    @ConstructorArgs({
            @Arg(column = "worldGuardRegionId", javaType = String.class),
            @Arg(column = "worldId", javaType = UUID.class),
            @Arg(column = "proposerRole", javaType = String.class),
            @Arg(column = "proposerId", javaType = UUID.class),
            @Arg(column = "newPrice", javaType = Double.class),
            @Arg(column = "newDurationSeconds", javaType = Long.class),
            @Arg(column = "newMaxExtensions", javaType = Integer.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "createdAt", javaType = LocalDateTime.class)
    })
    @NotNull List<LeaseholdModificationView> selectAwaitingByLandlord(@Param("landlordId") @NotNull UUID landlordId);

    @Override
    @Select("""
            SELECT rr.worldGuardRegionId, rr.worldId, m.proposerRole, m.proposerId,
                   m.newPrice, m.newDurationSeconds, m.newMaxExtensions, m.status, m.createdAt
            FROM LeaseholdModification m
            INNER JOIN Contract c ON c.contractId = m.leaseholdContractId AND c.contractType = 'leasehold'
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = c.realtyRegionId
            WHERE m.proposerId = #{proposerId}
            AND m.status IN ('AWAITING_LANDLORD', 'ACTIVE')
            ORDER BY m.createdAt DESC
            """)
    @ConstructorArgs({
            @Arg(column = "worldGuardRegionId", javaType = String.class),
            @Arg(column = "worldId", javaType = UUID.class),
            @Arg(column = "proposerRole", javaType = String.class),
            @Arg(column = "proposerId", javaType = UUID.class),
            @Arg(column = "newPrice", javaType = Double.class),
            @Arg(column = "newDurationSeconds", javaType = Long.class),
            @Arg(column = "newMaxExtensions", javaType = Integer.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "createdAt", javaType = LocalDateTime.class)
    })
    @NotNull List<LeaseholdModificationView> selectPendingByProposer(@Param("proposerId") @NotNull UUID proposerId);
}
