package io.github.md5sha256.realty.database.entity;

import io.github.md5sha256.realty.api.RealtySchematicFormat;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;

/**
 * Internal entity record mapping to the {@code RealtySchematic} DDL table.
 *
 * <p>One row per region: a re-capture replaces the previous schematic rather than
 * adding a version, so there is no history to page through.</p>
 *
 * @param realtyRegionId The {@code RealtyRegion} this schematic was captured from
 * @param data           The capture in Realty's own format, opening with the header
 *                       {@link RealtySchematicFormat} describes. Not a WorldEdit schematic
 * @param capturedAt     When the capture ran
 */
public record RealtySchematicEntity(
        int realtyRegionId,
        byte @NotNull [] data,
        @NotNull LocalDateTime capturedAt
) {
}
