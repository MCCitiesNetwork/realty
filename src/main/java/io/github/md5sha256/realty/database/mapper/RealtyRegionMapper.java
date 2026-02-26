package io.github.md5sha256.realty.database.mapper;

import io.github.md5sha256.realty.database.entity.RealtyRegionEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Base mapper interface for CRUD operations on the {@code RealtyRegion} table.
 * SQL annotations are provided by database-specific sub-interfaces.
 *
 * @see RealtyRegionEntity
 */
public interface RealtyRegionMapper {

    int registerWorldGuardRegion(@NotNull String worldGuardRegionId, @NotNull UUID worldId);

    @Nullable RealtyRegionEntity selectByWorldGuardRegion(@NotNull String worldGuardRegionId, @NotNull UUID worldId);

    int deleteByWorldGuardRegion(@NotNull String worldGuardRegionId, @NotNull UUID worldId);

    int deleteByRealtyRegionId(int realtyRegionId);

}
