package io.github.md5sha256.realty.rest;

import io.github.md5sha256.realty.database.Database;
import io.github.md5sha256.realty.database.SqlSessionWrapper;
import io.github.md5sha256.realty.database.entity.RealtyRegionEntity;
import io.github.md5sha256.realty.database.mapper.RealtyRegionMapper;
import io.github.md5sha256.realty.rest.json.RegionListResponse;
import io.github.md5sha256.realty.rest.json.WorldRef;
import io.javalin.http.Context;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * {@code GET /v1/regions} -- a page of every registered region.
 *
 * <p>The order is fixed by the mapper's query rather than chosen here, and is a
 * total order over the table, so paging through it cannot repeat or skip a row
 * the way an unordered {@code LIMIT}/{@code OFFSET} can.</p>
 */
final class RegionListHandler {

    private final Database database;
    private final WorldLookup worldLookup;
    private final RestSettings settings;

    RegionListHandler(@NotNull Database database,
                      @NotNull WorldLookup worldLookup,
                      @NotNull RestSettings settings) {
        this.database = database;
        this.worldLookup = worldLookup;
        this.settings = settings;
    }

    void handle(@NotNull Context ctx) {
        int page = QueryParams.page(ctx);
        int pageSize = QueryParams.pageSize(ctx, this.settings.maxPageSize());
        int offset = (page - 1) * pageSize;

        int totalCount;
        List<RealtyRegionEntity> rows;
        try (SqlSessionWrapper session = this.database.openSession(true)) {
            RealtyRegionMapper mapper = session.realtyRegionMapper();
            totalCount = mapper.countAll();
            rows = mapper.selectPage(pageSize, offset);
        }

        Set<UUID> worldIds = new HashSet<>();
        for (RealtyRegionEntity row : rows) {
            worldIds.add(row.worldId());
        }
        Map<UUID, WorldRef> worlds = this.worldLookup.refsFor(worldIds);

        List<RegionListResponse.Entry> entries = new ArrayList<>();
        for (RealtyRegionEntity row : rows) {
            entries.add(new RegionListResponse.Entry(
                    row.worldGuardRegionId(), worlds.get(row.worldId())));
        }

        ctx.json(new RegionListResponse(page, pageSize, totalCount,
                totalPages(totalCount, pageSize), entries));
    }

    private static int totalPages(int totalCount, int pageSize) {
        return (totalCount + pageSize - 1) / pageSize;
    }

}
