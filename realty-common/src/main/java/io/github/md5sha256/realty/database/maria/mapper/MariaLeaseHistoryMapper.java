package io.github.md5sha256.realty.database.maria.mapper;

import io.github.md5sha256.realty.database.mapper.LeaseHistoryMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public interface MariaLeaseHistoryMapper extends LeaseHistoryMapper {

    @Override
    @Insert("""
            INSERT INTO LeaseHistory (worldGuardRegionId, worldId, eventType, tenantId, landlordId,
                                      price, durationSeconds, extensionsRemaining)
            VALUES (#{worldGuardRegionId}, #{worldId}, #{eventType}, #{tenantId}, #{landlordId},
                    #{price}, #{durationSeconds}, #{extensionsRemaining})
            """)
    int insert(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
               @Param("worldId") @NotNull UUID worldId,
               @Param("eventType") @NotNull String eventType,
               @Param("tenantId") @NotNull UUID tenantId,
               @Param("landlordId") @NotNull UUID landlordId,
               @Param("price") @Nullable Double price,
               @Param("durationSeconds") @Nullable Long durationSeconds,
               @Param("extensionsRemaining") @Nullable Integer extensionsRemaining);

}
