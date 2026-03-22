package io.github.md5sha256.realty.database.entity;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Internal entity record mapping to the {@code RealtySign} DDL table.
 *
 * @param worldId        the world UUID
 * @param blockX         block X coordinate
 * @param blockY         block Y coordinate
 * @param blockZ         block Z coordinate
 * @param realtyRegionId FK to the RealtyRegion table
 * @param chunkX         chunk X coordinate (computed via {@code Math.floorDiv(blockX, 16)})
 * @param chunkZ         chunk Z coordinate (computed via {@code Math.floorDiv(blockZ, 16)})
 */
public record RealtySignEntity(
        @NotNull UUID worldId,
        int blockX,
        int blockY,
        int blockZ,
        int realtyRegionId,
        int chunkX,
        int chunkZ
) {
}
