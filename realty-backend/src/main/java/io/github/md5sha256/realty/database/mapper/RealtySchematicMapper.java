package io.github.md5sha256.realty.database.mapper;

import io.github.md5sha256.realty.database.entity.RealtySchematicEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Base mapper interface for the {@code RealtySchematic} table. SQL annotations are
 * provided by database-specific sub-interfaces.
 *
 * <p>Both methods are addressed by WorldGuard region plus world and join through
 * {@code RealtyRegion} internally, so callers never resolve a {@code realtyRegionId}
 * first.</p>
 *
 * @see RealtySchematicEntity
 */
public interface RealtySchematicMapper {

    /**
     * Stores {@code data} as the region's schematic, replacing any previous one.
     *
     * @return rows affected; {@code 0} when no such region is registered
     */
    int upsert(@NotNull String worldGuardRegionId,
               @NotNull UUID worldId,
               byte @NotNull [] data,
               @NotNull LocalDateTime capturedAt,
               @NotNull UUID capturedBy);

    @Nullable RealtySchematicEntity selectByWorldGuardRegion(@NotNull String worldGuardRegionId,
                                                            @NotNull UUID worldId);

}
