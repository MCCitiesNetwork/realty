package io.github.md5sha256.realty.database.entity;

import io.github.md5sha256.realty.api.Party;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One row of the server-wide activity feed: the three history tables unioned into a
 * single shape.
 *
 * <p>The two party columns are positional rather than named because the tables
 * disagree on who the parties are -- buyer and authority on a freehold event, tenant
 * and landlord on a leasehold one, agent and actor on an agent one. {@code kind} says
 * which pair {@code firstPlayerId} and {@code secondParty} hold, exactly as it says
 * which of the trailing columns are populated. Naming them for one table's meaning
 * would have made them lies in the other two. The first is always a player, and is
 * {@code null} on a leasehold event recorded while the region had no tenant; the
 * second may be any party.</p>
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
        @Nullable UUID firstPlayerId,
        @NotNull Party secondParty,
        @Nullable Double price,
        @Nullable Long durationSeconds,
        @Nullable Integer extensionsRemaining
) {
}
