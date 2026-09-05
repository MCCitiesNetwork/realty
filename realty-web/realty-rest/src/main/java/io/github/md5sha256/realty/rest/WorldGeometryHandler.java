package io.github.md5sha256.realty.rest;

import io.github.md5sha256.realty.database.Database;
import io.github.md5sha256.realty.database.SqlSessionWrapper;
import io.github.md5sha256.realty.database.entity.RealtyRegionEntity;
import io.github.md5sha256.realty.rest.json.RegionResponse;
import io.github.md5sha256.realty.rest.json.WorldGeometryResponse;
import io.github.md5sha256.realty.rest.module.ModuleClient;
import io.javalin.http.Context;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@code GET /v1/worlds/geometry?world=&page=&pageSize=} -- every registered region's footprint
 * in one world.
 *
 * <p>For drawing a map overlay. Both halves of the answer are gathered in bulk rather than per
 * region: the register comes from one database page, and the footprints from a reading of the
 * world that {@link GeometryCache} keeps between requests. A map of a built-up world is
 * therefore one reading of WorldGuard however many pages it takes and however many people are
 * looking at it, rather than a hop onto the game server's main thread for each.</p>
 *
 * <p>Geometry is enrichment here rather than the whole answer: the region list comes from the
 * database, so an unreachable module yields the page with null {@code dimensions} rather than a
 * 502. That is the same degradation {@code /v1/region} already applies.</p>
 */
final class WorldGeometryHandler {

    private final Database database;
    private final WorldLookup worldLookup;
    private final RestSettings settings;
    private final GeometryCache geometry;

    WorldGeometryHandler(@NotNull Database database,
                         @NotNull WorldLookup worldLookup,
                         @NotNull RestSettings settings,
                         @NotNull ModuleClient moduleClient) {
        this.database = database;
        this.worldLookup = worldLookup;
        this.settings = settings;
        this.geometry = new GeometryCache(
                Duration.ofSeconds(Math.max(0, settings.geometryCacheSeconds())),
                this::registeredRegionIds,
                moduleClient::dimensionsOf);
    }

    void handle(@NotNull Context ctx) {
        UUID worldId = this.worldLookup.resolve(QueryParams.required(ctx, "world"));
        int page = QueryParams.page(ctx);
        int pageSize = QueryParams.pageSize(ctx, this.settings.maxPageSize());
        int offset = (page - 1) * pageSize;

        int totalCount;
        List<RealtyRegionEntity> regions;
        try (SqlSessionWrapper session = this.database.openSession(true)) {
            totalCount = session.realtyRegionMapper().countByWorld(worldId);
            regions = session.realtyRegionMapper().selectPageByWorld(worldId, pageSize, offset);
        }

        List<String> ids = new ArrayList<>(regions.size());
        for (RealtyRegionEntity region : regions) {
            ids.add(region.worldGuardRegionId());
        }
        Map<String, RegionResponse.Dimensions> dimensions = this.geometry.forRegions(worldId, ids);

        // A browser paging through the same map, or coming back to it, need not ask again. The
        // minute is the site-wide one rather than the term a reading is kept for: a reading can
        // be replaced when a region moves, and a browser's copy cannot.
        ctx.header("Cache-Control", ResponseCaching.SHORT_LIVED);

        List<WorldGeometryResponse.Entry> entries = new ArrayList<>(regions.size());
        for (String id : ids) {
            entries.add(new WorldGeometryResponse.Entry(id, dimensions.get(id)));
        }
        ctx.json(new WorldGeometryResponse(page, pageSize, totalCount,
                (totalCount + pageSize - 1) / pageSize, this.worldLookup.refFor(worldId), entries));
    }

    /**
     * Every region id the register holds for a world, read in pages so one world's worth of
     * rows is never assembled by a single unbounded query.
     */
    private @NotNull List<String> registeredRegionIds(@NotNull UUID worldId) {
        List<String> ids = new ArrayList<>();
        try (SqlSessionWrapper session = this.database.openSession(true)) {
            int total = session.realtyRegionMapper().countByWorld(worldId);
            for (int offset = 0; offset < total; offset += GeometryCache.ID_PAGE) {
                List<RealtyRegionEntity> rows =
                        session.realtyRegionMapper().selectPageByWorld(worldId, GeometryCache.ID_PAGE, offset);
                if (rows.isEmpty()) {
                    break;
                }
                for (RealtyRegionEntity row : rows) {
                    ids.add(row.worldGuardRegionId());
                }
            }
        }
        return ids;
    }
}
