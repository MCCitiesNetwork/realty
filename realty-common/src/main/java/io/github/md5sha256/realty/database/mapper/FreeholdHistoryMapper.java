package io.github.md5sha256.realty.database.mapper;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

}
