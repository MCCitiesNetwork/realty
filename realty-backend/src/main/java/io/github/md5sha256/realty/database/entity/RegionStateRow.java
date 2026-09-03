package io.github.md5sha256.realty.database.entity;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A registered region and the state its contract implies, projected by one query.
 *
 * <p>Exists because the state of a listing row cannot be read from {@code RealtyRegion}
 * alone, and resolving it per row the way {@code getAllRegionsWithState} does costs two
 * further queries for every region on the page. The mapper derives it in SQL instead,
 * from the same {@code titleHolderId}/{@code tenantId} nullity
 * {@code RealtyBackendImpl#getRegionState} tests in Java.</p>
 *
 * @param state the {@code RegionState} name, or {@code null} for a registered region
 *              carrying no contract at all -- which {@code getAllRegionsWithState}
 *              silently drops rather than reports, and a listing must not
 */
public record RegionStateRow(
        int realtyRegionId,
        @NotNull String worldGuardRegionId,
        @NotNull UUID worldId,
        @Nullable String state
) {
}
