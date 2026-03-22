package io.github.md5sha256.realty.database.mapper;

import io.github.md5sha256.realty.database.entity.RealtySignEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Base mapper interface for CRUD operations on the {@code RealtySign} table.
 * SQL annotations are provided by database-specific sub-interfaces.
 *
 * @see RealtySignEntity
 */
public interface RealtySignMapper {

    /**
     * Inserts a new sign record. The caller must compute chunk coordinates
     * via {@code Math.floorDiv(blockX, 16)} and {@code Math.floorDiv(blockZ, 16)}.
     *
     * @param worldId            the world UUID of the sign
     * @param blockX             block X coordinate
     * @param blockY             block Y coordinate
     * @param blockZ             block Z coordinate
     * @param chunkX             chunk X coordinate
     * @param chunkZ             chunk Z coordinate
     * @param worldGuardRegionId the WG region name
     * @param regionWorldId      the world UUID of the region
     * @return number of rows inserted
     */
    int insert(@NotNull UUID worldId,
               int blockX,
               int blockY,
               int blockZ,
               int chunkX,
               int chunkZ,
               @NotNull String worldGuardRegionId,
               @NotNull UUID regionWorldId);

    /**
     * Selects a sign by its exact block position.
     *
     * @param worldId the world UUID
     * @param blockX  block X coordinate
     * @param blockY  block Y coordinate
     * @param blockZ  block Z coordinate
     * @return the sign entity, or null if not found
     */
    @Nullable RealtySignEntity selectByPosition(@NotNull UUID worldId,
                                                 int blockX,
                                                 int blockY,
                                                 int blockZ);

    /**
     * Selects all signs within a given chunk.
     *
     * @param worldId the world UUID
     * @param chunkX  chunk X coordinate
     * @param chunkZ  chunk Z coordinate
     * @return list of sign entities in the chunk
     */
    @NotNull List<RealtySignEntity> selectByChunk(@NotNull UUID worldId,
                                                   int chunkX,
                                                   int chunkZ);

    /**
     * Selects all signs linked to a given realty region.
     *
     * @param worldGuardRegionId the WG region name
     * @param worldId            the world UUID
     * @return list of sign entities for the region
     */
    @NotNull List<RealtySignEntity> selectByRegion(@NotNull String worldGuardRegionId,
                                                    @NotNull UUID worldId);

    /**
     * Deletes a sign by its exact block position.
     *
     * @param worldId the world UUID
     * @param blockX  block X coordinate
     * @param blockY  block Y coordinate
     * @param blockZ  block Z coordinate
     * @return number of rows deleted
     */
    int deleteByPosition(@NotNull UUID worldId,
                         int blockX,
                         int blockY,
                         int blockZ);

    /**
     * Deletes all signs linked to a given realty region.
     *
     * @param worldGuardRegionId the WG region name
     * @param worldId            the world UUID
     * @return number of rows deleted
     */
    int deleteByRegion(@NotNull String worldGuardRegionId,
                       @NotNull UUID worldId);
}
