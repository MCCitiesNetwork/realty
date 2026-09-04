package io.github.md5sha256.realty.rest.json;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Every registered region's geometry in one world, a page at a time.
 *
 * <p>For drawing a map overlay without one module call per region. {@code dimensions} is null
 * for a region Realty has registered but WorldGuard no longer holds -- the row is still
 * reported, because its absence from WorldGuard is itself worth seeing.</p>
 */
public record WorldGeometryResponse(int page,
                                    int pageSize,
                                    int totalCount,
                                    int totalPages,
                                    @NotNull WorldRef world,
                                    @NotNull List<Entry> regions) {

    public record Entry(@NotNull String worldGuardRegionId,
                        @Nullable RegionResponse.Dimensions dimensions) {
    }
}
