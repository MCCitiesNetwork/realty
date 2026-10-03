package io.github.md5sha256.realty.database.maria.mapper;

import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.database.entity.ActivityRow;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One row of the activity feed as read, with the history id of the event it came from.
 *
 * <p>The id is read only to keep two events apart. The second party is read through a
 * nested result map, and MyBatis then merges rows whose plain columns are all equal: two
 * events of the same kind on the same region in the same second, with the same players and
 * terms, came back as one. Kind and history id together are unique, so no two rows merge.</p>
 *
 * <p>Public for the same reason as {@link PartyAccountRow}: it is a mapper method's return type.</p>
 */
public record ActivityFeedRow(
        @NotNull String kind,
        int historyId,
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

    @NotNull ActivityRow toActivityRow() {
        return new ActivityRow(kind, worldGuardRegionId, worldId, eventType, eventTime,
                firstPlayerId, secondParty, price, durationSeconds, extensionsRemaining);
    }
}
