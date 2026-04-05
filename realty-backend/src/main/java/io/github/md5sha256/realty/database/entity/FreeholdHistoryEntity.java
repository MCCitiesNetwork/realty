package io.github.md5sha256.realty.database.entity;

import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

public record FreeholdHistoryEntity(
        int historyId,
        @NotNull String worldGuardRegionId,
        @NotNull UUID worldId,
        @NotNull String eventType,
        @NotNull UUID buyerId,
        @NotNull UUID authorityId,
        double price,
        @NotNull LocalDateTime eventTime
) {
}
