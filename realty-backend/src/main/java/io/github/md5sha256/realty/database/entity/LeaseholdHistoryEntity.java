package io.github.md5sha256.realty.database.entity;

import io.github.md5sha256.realty.api.Party;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.UUID;

public record LeaseholdHistoryEntity(
        int historyId,
        @NotNull String worldGuardRegionId,
        @NotNull UUID worldId,
        @NotNull String eventType,
        @Nullable UUID tenantId,
        @NotNull Party landlord,
        @Nullable Double price,
        @Nullable Long durationSeconds,
        @Nullable Integer extensionsRemaining,
        @NotNull LocalDateTime eventTime
) {
}
