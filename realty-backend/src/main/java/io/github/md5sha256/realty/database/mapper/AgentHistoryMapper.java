package io.github.md5sha256.realty.database.mapper;

import io.github.md5sha256.realty.database.entity.AgentHistoryEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface AgentHistoryMapper {

    int insert(@NotNull String worldGuardRegionId,
               @NotNull UUID worldId,
               @NotNull String eventType,
               @NotNull UUID agentId,
               @NotNull UUID actorId);

    @NotNull List<AgentHistoryEntity> searchHistory(@NotNull String worldGuardRegionId,
                                                     @NotNull UUID worldId,
                                                     @Nullable String eventType,
                                                     @Nullable LocalDateTime since,
                                                     @Nullable UUID playerId,
                                                     int limit,
                                                     int offset);

    int countHistory(@NotNull String worldGuardRegionId,
                     @NotNull UUID worldId,
                     @Nullable String eventType,
                     @Nullable LocalDateTime since,
                     @Nullable UUID playerId);
}
