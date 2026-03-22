package io.github.md5sha256.realty.database.entity;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.UUID;

public sealed interface HistoryEntry permits HistoryEntry.Freehold, HistoryEntry.Lease {

    @NotNull String eventType();

    @NotNull LocalDateTime eventTime();

    record Freehold(
            @NotNull String eventType,
            @NotNull LocalDateTime eventTime,
            @NotNull UUID buyerId,
            @NotNull UUID authorityId,
            double price
    ) implements HistoryEntry {}

    record Lease(
            @NotNull String eventType,
            @NotNull LocalDateTime eventTime,
            @NotNull UUID tenantId,
            @NotNull UUID landlordId,
            @Nullable Double price,
            @Nullable Long durationSeconds,
            @Nullable Integer extensionsRemaining
    ) implements HistoryEntry {}
}
