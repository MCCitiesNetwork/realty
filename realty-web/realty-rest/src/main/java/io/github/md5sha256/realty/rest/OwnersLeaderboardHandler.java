package io.github.md5sha256.realty.rest;

import io.github.md5sha256.realty.database.Database;
import io.github.md5sha256.realty.database.SqlSessionWrapper;
import io.github.md5sha256.realty.database.entity.PlotOwnerCount;
import io.github.md5sha256.realty.database.mapper.FreeholdContractMapper;
import io.github.md5sha256.realty.rest.json.OwnersLeaderboardResponse;
import io.github.md5sha256.realty.rest.json.PlayerRef;
import io.github.md5sha256.realty.rest.module.ModuleClient;
import io.github.md5sha256.realty.rest.module.PlayerNames;
import io.javalin.http.Context;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * {@code GET /v1/leaderboard/owners} -- title holders ranked by plot count.
 *
 * <p>The ranking and the paging both live in SQL. Reading every owner and sorting here
 * would grow with the estate rather than with the page, and the ordering has to match
 * between the page query and any later one for paging to be stable at all.</p>
 */
final class OwnersLeaderboardHandler {

    private final Database database;
    private final RestSettings settings;
    private final ModuleClient moduleClient;

    OwnersLeaderboardHandler(@NotNull Database database,
                             @NotNull RestSettings settings,
                             @NotNull ModuleClient moduleClient) {
        this.database = database;
        this.settings = settings;
        this.moduleClient = moduleClient;
    }

    void handle(@NotNull Context ctx) {
        int page = QueryParams.page(ctx);
        int pageSize = QueryParams.pageSize(ctx, this.settings.maxPageSize());
        int offset = (page - 1) * pageSize;

        int totalCount;
        List<PlotOwnerCount> rows;
        try (SqlSessionWrapper session = this.database.openSession(true)) {
            FreeholdContractMapper mapper = session.freeholdContractMapper();
            totalCount = mapper.countDistinctTitleHolders();
            rows = mapper.selectPlotCountsByTitleHolderPaged(pageSize, offset);
        }

        List<UUID> ownerIds = new ArrayList<>(rows.size());
        for (PlotOwnerCount row : rows) {
            ownerIds.add(row.titleHolderId());
        }
        // One module call for the whole page, so a full page costs the same hop as a
        // single row; an unreachable module leaves every name null rather than failing.
        Map<UUID, String> names = PlayerNames.resolve(this.moduleClient, ownerIds);

        List<OwnersLeaderboardResponse.Entry> owners = new ArrayList<>(rows.size());
        int rank = offset + 1;
        for (PlotOwnerCount row : rows) {
            PlayerRef player = Objects.requireNonNull(PlayerNames.ref(row.titleHolderId(), names));
            owners.add(new OwnersLeaderboardResponse.Entry(rank++, player, row.plotCount()));
        }

        ctx.json(new OwnersLeaderboardResponse(page, pageSize, totalCount,
                totalPages(totalCount, pageSize), owners));
    }

    private static int totalPages(int totalCount, int pageSize) {
        return (totalCount + pageSize - 1) / pageSize;
    }
}
