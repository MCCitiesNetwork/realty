package io.github.md5sha256.realty.database.mapper;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public interface LeaseHistoryMapper {

    int insert(@NotNull String worldGuardRegionId,
               @NotNull UUID worldId,
               @NotNull String eventType,
               @NotNull UUID tenantId,
               @NotNull UUID landlordId,
               @Nullable Double price,
               @Nullable Long durationSeconds,
               @Nullable Integer extensionsRemaining);

}
