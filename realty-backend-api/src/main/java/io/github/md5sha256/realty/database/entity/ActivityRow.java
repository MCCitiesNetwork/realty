package io.github.md5sha256.realty.database.entity;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One row of the server-wide activity feed: the three history tables unioned into a
 * single shape.
 *
 * <p>The two player columns are positional rather than named because the tables
 * disagree on who the parties are -- buyer and authority on a freehold event, tenant
 * and landlord on a leasehold one, agent and actor on an agent one. {@code kind} says
 * which pair {@code firstPlayerId} and {@code secondPlayerId} hold, exactly as it says
 * which of the trailing columns are populated. Naming them for one table's meaning
 * would have made them lies in the other two.</p>
 *
 * <p>Every history table carries {@code worldGuardRegionId} and {@code worldId}
 * directly, so the union needs no join back to {@code RealtyRegion}.</p>
 */
public record ActivityRow(
        @NotNull String kind,
        @NotNull String worldGuardRegionId,
        @NotNull UUID worldId,
        @NotNull String eventType,
        @NotNull LocalDateTime eventTime,
        @NotNull UUID firstPlayerId,
        @NotNull UUID secondPlayerId,
        @Nullable Double price,
        @Nullable Long durationSeconds,
        @Nullable Integer extensionsRemaining
) {
}
