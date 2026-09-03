package io.github.md5sha256.realty.rest;

import io.github.md5sha256.realty.database.Database;
import io.github.md5sha256.realty.database.SqlSessionWrapper;
import io.github.md5sha256.realty.database.entity.RealtyRegionEntity;
import io.github.md5sha256.realty.rest.json.RegionResponse;
import io.github.md5sha256.realty.rest.json.WorldGeometryResponse;
import io.github.md5sha256.realty.rest.module.ModuleClient;
import io.javalin.http.Context;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@code GET /v1/worlds/geometry?world=&page=&pageSize=} -- every registered region's footprint
 * in one world.
 *
 * <p>For drawing a map overlay. The point of the route is that a page of regions costs the game
 * server one main-thread hop rather than one per region, which is why it batches rather than
 * leaving a client to loop {@code /v1/region}.</p>
 *
 * <p>Geometry is enrichment here rather than the whole answer: the region list comes from the
 * database, so an unreachable module yields the page with null {@code dimensions} rather than a
 * 502. That is the same degradation {@code /v1/region} already applies.</p>
 */
final class WorldGeometryHandler {

    private final Database database;
    private final WorldLookup worldLookup;
    private final RestSettings settings;
    private final ModuleClient moduleClient;

    WorldGeometryHandler(@NotNull Database database,
                         @NotNull WorldLookup worldLookup,
                         @NotNull RestSettings settings,
                         @NotNull ModuleClient moduleClient) {
        this.database = database;
        this.worldLookup = worldLookup;
        this.settings = settings;
        this.moduleClient = moduleClient;
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
        Map<String, RegionResponse.Dimensions> dimensions =
                ids.isEmpty() ? Map.of() : this.moduleClient.dimensionsOf(worldId, ids);

        List<WorldGeometryResponse.Entry> entries = new ArrayList<>(regions.size());
        for (String id : ids) {
            entries.add(new WorldGeometryResponse.Entry(id, dimensions.get(id)));
        }
        ctx.json(new WorldGeometryResponse(page, pageSize, totalCount,
                (totalCount + pageSize - 1) / pageSize, this.worldLookup.refFor(worldId), entries));
    }
}
