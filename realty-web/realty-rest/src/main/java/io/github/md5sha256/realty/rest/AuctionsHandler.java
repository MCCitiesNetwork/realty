package io.github.md5sha256.realty.rest;

import io.github.md5sha256.realty.database.Database;
import io.github.md5sha256.realty.database.SqlSessionWrapper;
import io.github.md5sha256.realty.database.entity.ActiveAuctionRow;
import io.github.md5sha256.realty.database.entity.AuctionSort;
import io.github.md5sha256.realty.database.mapper.FreeholdContractAuctionMapper;
import io.github.md5sha256.realty.rest.json.AuctionsResponse;
import io.github.md5sha256.realty.rest.json.WorldRef;
import io.github.md5sha256.realty.rest.module.ModuleClient;
import io.github.md5sha256.realty.rest.module.PlayerNames;
import io.javalin.http.Context;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * {@code GET /v1/auctions} -- the auctions currently taking bids.
 *
 * <p>The deadline each row carries is computed in SQL rather than here, because
 * {@code sort=ending_soon} orders by it. A deadline derived in Java after paging would
 * order the page rather than the listing, and page 2 could then hold an auction closing
 * before anything on page 1.</p>
 */
final class AuctionsHandler {

    private final Database database;
    private final WorldLookup worldLookup;
    private final RestSettings settings;
    private final ModuleClient moduleClient;

    AuctionsHandler(@NotNull Database database,
                    @NotNull WorldLookup worldLookup,
                    @NotNull RestSettings settings,
                    @NotNull ModuleClient moduleClient) {
        this.database = database;
        this.worldLookup = worldLookup;
        this.settings = settings;
        this.moduleClient = moduleClient;
    }

    void handle(@NotNull Context ctx) {
        String worldParam = QueryParams.optional(ctx, "world");
        UUID worldId = worldParam == null ? null : this.worldLookup.resolve(worldParam);
        AuctionSort sort = sort(ctx);

        int page = QueryParams.page(ctx);
        int pageSize = QueryParams.pageSize(ctx, this.settings.maxPageSize());
        int offset = (page - 1) * pageSize;

        int totalCount;
        List<ActiveAuctionRow> rows;
        try (SqlSessionWrapper session = this.database.openSession(true)) {
            FreeholdContractAuctionMapper mapper = session.freeholdContractAuctionMapper();
            totalCount = mapper.countActiveInWorld(worldId);
            rows = mapper.selectActivePage(worldId, sort, pageSize, offset);
        }

        List<UUID> playerIds = new ArrayList<>();
        Set<UUID> worldIds = new HashSet<>();
        for (ActiveAuctionRow row : rows) {
            playerIds.add(row.auctioneerId());
            if (row.highestBidderId() != null) {
                playerIds.add(row.highestBidderId());
            }
            worldIds.add(row.worldId());
        }
        Map<UUID, String> names = PlayerNames.resolve(this.moduleClient, playerIds);
        Map<UUID, WorldRef> worlds = this.worldLookup.refsFor(worldIds);

        List<AuctionsResponse.Entry> auctions = new ArrayList<>(rows.size());
        for (ActiveAuctionRow row : rows) {
            auctions.add(toEntry(row, worlds, names));
        }

        ctx.json(new AuctionsResponse(page, pageSize, totalCount,
                totalPages(totalCount, pageSize), auctions));
    }

    private static @NotNull AuctionSort sort(@NotNull Context ctx) {
        String raw = QueryParams.optional(ctx, "sort");
        if (raw == null) {
            return AuctionSort.ENDING_SOON;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "ending_soon" -> AuctionSort.ENDING_SOON;
            case "highest_bid" -> AuctionSort.HIGHEST_BID;
            default -> throw ApiException.badRequest("INVALID_SORT",
                    "Query parameter 'sort' must be one of [ending_soon, highest_bid]");
        };
    }

    private static @NotNull AuctionsResponse.Entry toEntry(@NotNull ActiveAuctionRow row,
                                                           @NotNull Map<UUID, WorldRef> worlds,
                                                           @NotNull Map<UUID, String> names) {
        return new AuctionsResponse.Entry(
                row.worldGuardRegionId(),
                worlds.get(row.worldId()),
                Objects.requireNonNull(PlayerNames.ref(row.auctioneerId(), names)),
                IsoDates.format(row.startDate()),
                IsoDates.format(row.endDate()),
                row.minBid(),
                row.minStep(),
                row.biddingDurationSeconds(),
                row.paymentDurationSeconds(),
                toBid(row, names),
                row.bidderCount());
    }

    /**
     * The three highest-bid columns are null together, for an auction nobody has bid
     * on. All three are tested rather than just the id so the record below cannot be
     * built from a half-populated row.
     */
    private static @Nullable AuctionsResponse.Bid toBid(@NotNull ActiveAuctionRow row,
                                                        @NotNull Map<UUID, String> names) {
        if (row.highestBidderId() == null || row.highestBidPrice() == null
                || row.highestBidTime() == null) {
            return null;
        }
        return new AuctionsResponse.Bid(
                Objects.requireNonNull(PlayerNames.ref(row.highestBidderId(), names)),
                row.highestBidPrice(),
                IsoDates.format(row.highestBidTime()));
    }

    private static int totalPages(int totalCount, int pageSize) {
        return (totalCount + pageSize - 1) / pageSize;
    }
}
