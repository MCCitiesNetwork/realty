package io.github.md5sha256.realty.database.maria.mapper;

import io.github.md5sha256.realty.database.entity.LeaseHistoryEntity;
import io.github.md5sha256.realty.database.mapper.LeaseHistoryMapper;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.SelectProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.List;
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

    @Override
    @SelectProvider(type = LeaseHistorySqlProvider.class, method = "searchHistory")
    @ConstructorArgs({
            @Arg(column = "historyId", javaType = int.class),
            @Arg(column = "worldGuardRegionId", javaType = String.class),
            @Arg(column = "worldId", javaType = UUID.class),
            @Arg(column = "eventType", javaType = String.class),
            @Arg(column = "tenantId", javaType = UUID.class),
            @Arg(column = "landlordId", javaType = UUID.class),
            @Arg(column = "price", javaType = Double.class),
            @Arg(column = "durationSeconds", javaType = Long.class),
            @Arg(column = "extensionsRemaining", javaType = Integer.class),
            @Arg(column = "eventTime", javaType = LocalDateTime.class)
    })
    @NotNull List<LeaseHistoryEntity> searchHistory(
            @Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
            @Param("worldId") @NotNull UUID worldId,
            @Param("eventType") @Nullable String eventType,
            @Param("since") @Nullable LocalDateTime since,
            @Param("playerId") @Nullable UUID playerId,
            @Param("limit") int limit,
            @Param("offset") int offset);

    @Override
    @SelectProvider(type = LeaseHistorySqlProvider.class, method = "countHistory")
    int countHistory(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                     @Param("worldId") @NotNull UUID worldId,
                     @Param("eventType") @Nullable String eventType,
                     @Param("since") @Nullable LocalDateTime since,
                     @Param("playerId") @Nullable UUID playerId);

}
