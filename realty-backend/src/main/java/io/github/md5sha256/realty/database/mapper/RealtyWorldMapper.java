package io.github.md5sha256.realty.database.mapper;

import io.github.md5sha256.realty.database.entity.RealtyWorldEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Mapper for the world UUID to world name lookup table.
 *
 * @see RealtyWorldEntity
 */
public interface RealtyWorldMapper {

    void upsert(@NotNull UUID worldId, @NotNull String worldName);

    @NotNull List<RealtyWorldEntity> selectAll();

    @Nullable RealtyWorldEntity selectById(@NotNull UUID worldId);

    @Nullable RealtyWorldEntity selectByName(@NotNull String worldName);

}
