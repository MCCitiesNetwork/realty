package io.github.md5sha256.realty.database.mapper;

import io.github.md5sha256.realty.database.entity.LeaseholdHistoryEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface LeaseholdHistoryMapper {

    int insert(@NotNull String worldGuardRegionId,
               @NotNull UUID worldId,
               @NotNull String eventType,
               @Nullable UUID tenantId,
               int landlordPartyId,
               @Nullable Double price,
               @Nullable Long durationSeconds,
               @Nullable Integer extensionsRemaining);

    /**
     * As {@link #insert}, and answers with the id of the row it wrote, so that the row
     * can be removed again by {@link #deleteById} if what it records is undone.
     */
    int insertReturningId(@NotNull String worldGuardRegionId,
                          @NotNull UUID worldId,
                          @NotNull String eventType,
                          @Nullable UUID tenantId,
                          int landlordPartyId,
                          @Nullable Double price,
                          @Nullable Long durationSeconds,
                          @Nullable Integer extensionsRemaining);

    /** Removes one record. For taking back a record of something that was then undone. */
    int deleteById(int historyId);

    @NotNull List<LeaseholdHistoryEntity> searchHistory(@NotNull String worldGuardRegionId,
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
