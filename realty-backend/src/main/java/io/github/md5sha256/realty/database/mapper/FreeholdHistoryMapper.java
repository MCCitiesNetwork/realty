package io.github.md5sha256.realty.database.mapper;

import io.github.md5sha256.realty.database.entity.FreeholdHistoryEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface FreeholdHistoryMapper {

    int insert(@NotNull String worldGuardRegionId,
               @NotNull UUID worldId,
               @NotNull String eventType,
               @NotNull UUID buyerId,
               @NotNull UUID authorityId,
               double price);

    @Nullable Double selectLastFreeholdPrice(@NotNull String worldGuardRegionId,
                                          @NotNull UUID worldId);

    @NotNull List<FreeholdHistoryEntity> searchHistory(@NotNull String worldGuardRegionId,
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
