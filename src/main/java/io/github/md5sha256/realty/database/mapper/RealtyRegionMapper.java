package io.github.md5sha256.realty.database.mapper;

import io.github.md5sha256.realty.database.entity.RealtyRegionEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
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

    @NotNull List<RealtyRegionEntity> selectRegionsByTitleHolder(@NotNull UUID playerId, int limit, int offset);

    @NotNull List<RealtyRegionEntity> selectRegionsByAuthority(@NotNull UUID playerId, int limit, int offset);

    @NotNull List<RealtyRegionEntity> selectRegionsByTenant(@NotNull UUID playerId, int limit, int offset);

    int countRegionsByTitleHolder(@NotNull UUID playerId);

    int countRegionsByAuthority(@NotNull UUID playerId);

    int countRegionsByTenant(@NotNull UUID playerId);

}
