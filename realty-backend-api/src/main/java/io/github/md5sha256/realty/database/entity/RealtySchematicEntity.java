package io.github.md5sha256.realty.database.entity;

import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;

/**
 * Internal entity record mapping to the {@code RealtySchematic} DDL table.
 *
 * <p>One row per region: a re-capture replaces the previous schematic rather than
 * adding a version, so there is no history to page through.</p>
 *
 * @param realtyRegionId The {@code RealtyRegion} this schematic was captured from
 * @param data           Sponge Schematic v3 bytes, as written by WorldEdit
 * @param capturedAt     When the capture ran
 */
public record RealtySchematicEntity(
        int realtyRegionId,
        byte @NotNull [] data,
        @NotNull LocalDateTime capturedAt
) {
}
