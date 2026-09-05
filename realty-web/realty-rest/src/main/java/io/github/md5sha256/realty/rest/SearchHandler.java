package io.github.md5sha256.realty.rest;

import io.github.md5sha256.realty.database.Database;
import io.github.md5sha256.realty.database.SqlSessionWrapper;
import io.github.md5sha256.realty.database.entity.OccupancyFilter;
import io.github.md5sha256.realty.database.entity.SearchResultEntity;
import io.github.md5sha256.realty.database.entity.SearchSort;
import io.github.md5sha256.realty.database.mapper.SearchMapper;
import io.github.md5sha256.realty.rest.json.SearchResponse;
import io.github.md5sha256.realty.rest.json.WorldRef;
import io.javalin.http.Context;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * {@code GET /v1/regions/search} -- the bulk browse endpoint, backed by the same
 * {@link SearchMapper} query the in-game {@code /realty search} dialog uses.
 *
 * <p>Goes straight to the mapper rather than through {@code RealtyBackend}, which
 * has no search method; this is the same route {@code SearchDialog} takes.</p>
 */
final class SearchHandler {

    /**
     * The open upper bound when no {@code maxPrice} is given, matching what
     * {@code SearchCommand} passes for an unset {@code --max-price} flag.
     */
    private static final double NO_MAX_PRICE = Double.MAX_VALUE;

    private final Database database;
    private final WorldLookup worldLookup;
    private final RestSettings settings;

    SearchHandler(@NotNull Database database,
                  @NotNull WorldLookup worldLookup,
                  @NotNull RestSettings settings) {
        this.database = database;
        this.worldLookup = worldLookup;
        this.settings = settings;
    }

    void handle(@NotNull Context ctx) {
        TypeFilter type = parseType(QueryParams.optional(ctx, "type"));

        String worldParam = QueryParams.optional(ctx, "world");
        UUID worldId = worldParam == null ? null : this.worldLookup.resolve(worldParam);

        Double minPrice = QueryParams.optionalDouble(ctx, "minPrice", "INVALID_PRICE");
        Double maxPrice = QueryParams.optionalDouble(ctx, "maxPrice", "INVALID_PRICE");
        if (minPrice != null && minPrice < 0) {
            throw ApiException.badRequest("INVALID_PRICE", "Query parameter 'minPrice' must not be negative");
        }
        if (maxPrice != null && maxPrice < 0) {
            throw ApiException.badRequest("INVALID_PRICE", "Query parameter 'maxPrice' must not be negative");
        }
        if (minPrice != null && maxPrice != null && minPrice > maxPrice) {
            throw ApiException.badRequest("INVALID_PRICE", "minPrice must not exceed maxPrice");
        }
        double effectiveMin = minPrice == null ? 0.0 : minPrice;
        double effectiveMax = maxPrice == null ? NO_MAX_PRICE : maxPrice;

        List<String> tags = QueryParams.values(ctx, "tag");
        // Exclusion is a mapper capability the browse UI has no use for yet; an
        // empty collection is how the query says "no tag filter".
        Collection<String> tagIds = tags.isEmpty() ? null : tags;

        OccupancyFilter occupancy = parseOccupancy(QueryParams.optional(ctx, "occupancy"));
        SearchSort sort = parseSort(QueryParams.optional(ctx, "sort"));

        int page = QueryParams.page(ctx);
        int pageSize = QueryParams.pageSize(ctx, this.settings.maxPageSize());
        int offset = (page - 1) * pageSize;

        int totalCount;
        List<SearchResultEntity> rows;
        try (SqlSessionWrapper session = this.database.openSession(true)) {
            SearchMapper mapper = session.searchMapper();
            totalCount = mapper.searchCount(type.freehold, type.leasehold, type.unpricedFreehold,
                    worldId, tagIds, null, effectiveMin, effectiveMax, occupancy);
            rows = mapper.search(type.freehold, type.leasehold, type.unpricedFreehold,
                    worldId, tagIds, null, effectiveMin, effectiveMax, occupancy, sort, pageSize, offset);
        }

        Set<UUID> worldIds = new HashSet<>();
        for (SearchResultEntity row : rows) {
            worldIds.add(row.worldId());
        }
        Map<UUID, WorldRef> worlds = this.worldLookup.refsFor(worldIds);

        List<SearchResponse.Result> results = new ArrayList<>();
        for (SearchResultEntity row : rows) {
            results.add(new SearchResponse.Result(
                    row.worldGuardRegionId(),
                    worlds.get(row.worldId()),
                    row.contractType(),
                    row.price(),
                    row.state(),
                    row.durationSeconds()));
        }

        ctx.json(new SearchResponse(page, pageSize, totalCount,
                totalPages(totalCount, pageSize), results));
    }

    /**
     * The {@code type} axis has two readings of "freehold": what is on the market
     * (an asking price is set) and what exists (any freehold contract). {@code sale}
     * and the default {@code all} are the market view, which is what the in-game
     * search shows; {@code freehold} widens to every freehold, whose unlisted rows
     * come back with a null price. Leaseholds always carry a price, so {@code rent}
     * and {@code leasehold} are the same set and differ only in name.
     */
    private enum TypeFilter {
        SALE(true, false, false),
        RENT(false, true, false),
        ALL(true, true, false),
        FREEHOLD(true, false, true),
        LEASEHOLD(false, true, false);

        final boolean freehold;
        final boolean leasehold;
        final boolean unpricedFreehold;

        TypeFilter(boolean freehold, boolean leasehold, boolean unpricedFreehold) {
            this.freehold = freehold;
            this.leasehold = leasehold;
            this.unpricedFreehold = unpricedFreehold;
        }
    }

    private static @NotNull TypeFilter parseType(@Nullable String raw) {
        String value = valueOr(raw, "all");
        return switch (value) {
            case "sale" -> TypeFilter.SALE;
            case "rent" -> TypeFilter.RENT;
            case "all" -> TypeFilter.ALL;
            case "freehold" -> TypeFilter.FREEHOLD;
            case "leasehold" -> TypeFilter.LEASEHOLD;
            default -> throw ApiException.badRequest("INVALID_TYPE",
                    "Query parameter 'type' must be one of [sale, rent, all, freehold, leasehold], got '"
                            + value + "'");
        };
    }

    private static @NotNull OccupancyFilter parseOccupancy(@Nullable String raw) {
        String value = valueOr(raw, "any");
        return switch (value) {
            case "any" -> OccupancyFilter.IGNORE;
            case "occupied" -> OccupancyFilter.OCCUPIED;
            case "unoccupied" -> OccupancyFilter.UNOCCUPIED;
            default -> throw ApiException.badRequest("INVALID_OCCUPANCY",
                    "Query parameter 'occupancy' must be one of [any, occupied, unoccupied], got '"
                            + value + "'");
        };
    }

    private static @NotNull SearchSort parseSort(@Nullable String raw) {
        String value = valueOr(raw, "price_desc");
        return switch (value) {
            case "price_desc" -> SearchSort.PRICE_DESC;
            case "price_asc" -> SearchSort.PRICE_ASC;
            default -> throw ApiException.badRequest("INVALID_SORT",
                    "Query parameter 'sort' must be one of [price_desc, price_asc], got '" + value + "'");
        };
    }

    private static @NotNull String valueOr(@Nullable String raw, @NotNull String fallback) {
        return raw == null ? fallback : raw.trim();
    }

    private static int totalPages(int totalCount, int pageSize) {
        return (totalCount + pageSize - 1) / pageSize;
    }

}
