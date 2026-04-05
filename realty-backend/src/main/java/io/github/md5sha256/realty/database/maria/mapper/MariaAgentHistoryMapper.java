package io.github.md5sha256.realty.database.maria.mapper;

import io.github.md5sha256.realty.database.entity.AgentHistoryEntity;
import io.github.md5sha256.realty.database.mapper.AgentHistoryMapper;
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

public interface MariaAgentHistoryMapper extends AgentHistoryMapper {

    @Override
    @Insert("""
            INSERT INTO AgentHistory (worldGuardRegionId, worldId, eventType, agentId, actorId)
            VALUES (#{worldGuardRegionId}, #{worldId}, #{eventType}, #{agentId}, #{actorId})
            """)
    int insert(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
               @Param("worldId") @NotNull UUID worldId,
               @Param("eventType") @NotNull String eventType,
               @Param("agentId") @NotNull UUID agentId,
               @Param("actorId") @NotNull UUID actorId);

    @Override
    @SelectProvider(type = AgentHistorySqlProvider.class, method = "searchHistory")
    @ConstructorArgs({
            @Arg(column = "historyId", javaType = int.class),
            @Arg(column = "worldGuardRegionId", javaType = String.class),
            @Arg(column = "worldId", javaType = UUID.class),
            @Arg(column = "eventType", javaType = String.class),
            @Arg(column = "agentId", javaType = UUID.class),
            @Arg(column = "actorId", javaType = UUID.class),
            @Arg(column = "eventTime", javaType = LocalDateTime.class)
    })
    @NotNull List<AgentHistoryEntity> searchHistory(
            @Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
            @Param("worldId") @NotNull UUID worldId,
            @Param("eventType") @Nullable String eventType,
            @Param("since") @Nullable LocalDateTime since,
            @Param("playerId") @Nullable UUID playerId,
            @Param("limit") int limit,
            @Param("offset") int offset);

    @Override
    @SelectProvider(type = AgentHistorySqlProvider.class, method = "countHistory")
    int countHistory(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                     @Param("worldId") @NotNull UUID worldId,
                     @Param("eventType") @Nullable String eventType,
                     @Param("since") @Nullable LocalDateTime since,
                     @Param("playerId") @Nullable UUID playerId);
}
