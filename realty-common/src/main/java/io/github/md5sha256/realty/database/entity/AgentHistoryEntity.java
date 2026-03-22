package io.github.md5sha256.realty.database.entity;

import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

public record AgentHistoryEntity(
        int historyId,
        @NotNull String worldGuardRegionId,
        @NotNull UUID worldId,
        @NotNull String eventType,
        @NotNull UUID agentId,
        @NotNull UUID actorId,
        @NotNull LocalDateTime eventTime
) {
}
