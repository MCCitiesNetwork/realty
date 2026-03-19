package io.github.md5sha256.realty.database.entity;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Internal entity record mapping to the {@code RealtyRegion} DDL table.
 *
 * @param realtyRegionId     Auto-increment primary key
 * @param worldGuardRegionId WorldGuard region identifier
 * @param worldId            UUID of the world containing this region
 * @see io.github.md5sha256.realty.api.RealtyRegion
 */
public record RealtyRegionEntity(
        int realtyRegionId,
        @NotNull String worldGuardRegionId,
        @NotNull UUID worldId
) {
}
