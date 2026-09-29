package io.github.md5sha256.realty.database.entity;

import io.github.md5sha256.realty.api.Party;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.UUID;

public sealed interface HistoryEntry permits HistoryEntry.Freehold, HistoryEntry.Leasehold, HistoryEntry.Agent {

    @NotNull String eventType();

    @NotNull LocalDateTime eventTime();

    record Freehold(
            @NotNull String eventType,
            @NotNull LocalDateTime eventTime,
            @NotNull UUID buyerId,
            @NotNull Party authority,
            double price
    ) implements HistoryEntry {}

    record Leasehold(
            @NotNull String eventType,
            @NotNull LocalDateTime eventTime,
            @Nullable UUID tenantId,
            @NotNull Party landlord,
            @Nullable Double price,
            @Nullable Long durationSeconds,
            @Nullable Integer extensionsRemaining
    ) implements HistoryEntry {}

    record Agent(
            @NotNull String eventType,
            @NotNull LocalDateTime eventTime,
            @NotNull UUID agentId,
            @NotNull UUID actorId
    ) implements HistoryEntry {}
}
