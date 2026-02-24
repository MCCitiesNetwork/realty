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

    void insert(@NotNull RealtyRegionEntity entity);

    @Nullable RealtyRegionEntity selectById(int id);

    @NotNull List<RealtyRegionEntity> selectByWorldId(@NotNull UUID worldId);

    @NotNull List<RealtyRegionEntity> selectAll();

    void update(@NotNull RealtyRegionEntity entity);

    void deleteById(int id);
}
