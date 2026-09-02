package io.github.md5sha256.realty.rest;

import io.github.md5sha256.realty.api.RealtyBackend;
import io.github.md5sha256.realty.database.Database;
import io.github.md5sha256.realty.database.SqlSessionWrapper;
import io.github.md5sha256.realty.database.entity.RealtyRegionEntity;
import io.github.md5sha256.realty.database.entity.RentedRegionView;
import io.github.md5sha256.realty.rest.json.PlayerRef;
import io.github.md5sha256.realty.rest.json.PlayerRegionsResponse;
import io.github.md5sha256.realty.rest.json.WorldRef;
import io.javalin.http.Context;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * {@code GET /v1/players/regions?player=...} -- the HTTP form of {@code /realty list}.
 */
final class PlayerRegionsHandler {

    private final RealtyBackend backend;
    private final Database database;
    private final WorldLookup worldLookup;
    private final RestSettings settings;

    PlayerRegionsHandler(@NotNull RealtyBackend backend,
                         @NotNull Database database,
                         @NotNull WorldLookup worldLookup,
                         @NotNull RestSettings settings) {
        this.backend = backend;
        this.database = database;
        this.worldLookup = worldLookup;
        this.settings = settings;
    }

    void handle(@NotNull Context ctx) {
        String playerParam = QueryParams.required(ctx, "player");
        UUID playerId = resolvePlayerId(playerParam);

        String category = ctx.queryParam("category");
        if (category == null || category.isBlank()) {
            category = "all";
        }

        int page = parsePage(ctx.queryParam("page"));
        int pageSize = parsePageSize(ctx.queryParam("pageSize"));
        int offset = (page - 1) * pageSize;

        PlayerRef player = new PlayerRef(playerId.toString(), null);

        PlayerRegionsResponse response = switch (category) {
            case "owned" -> handleOwned(player, playerId, page, pageSize, offset);
            case "rented" -> handleRented(player, playerId, page, pageSize, offset);
            default -> handleAll(player, playerId, page, pageSize, offset);
        };

        ctx.json(response);
    }

    /**
     * Determines whether {@code param} should be resolved as a UUID or a player
     * name. A UUID-shaped value (36 characters, hyphens at the standard positions)
     * that fails to parse is treated as a malformed UUID and rejected with 400; any
     * other non-UUID value is treated as a player name, which cannot be resolved
     * until the query-service enrichment client ships, so it is rejected with 502.
     */
    private static @NotNull UUID resolvePlayerId(@NotNull String param) {
        if (isUuidShaped(param)) {
            try {
                return UUID.fromString(param);
            } catch (IllegalArgumentException ex) {
                throw ApiException.badRequest("MALFORMED_UUID",
                        "Query parameter 'player' is not a valid UUID");
            }
        }
        throw ApiException.badGateway("NAME_LOOKUP_UNAVAILABLE",
                "Player name lookup requires the query-service module");
    }

    private static boolean isUuidShaped(@NotNull String value) {
        return value.length() == 36
                && value.charAt(8) == '-'
                && value.charAt(13) == '-'
                && value.charAt(18) == '-'
                && value.charAt(23) == '-';
    }

    private int parsePage(String raw) {
        if (raw == null || raw.isBlank()) {
            return 1;
        }
        int page;
        try {
            page = Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            throw ApiException.badRequest("INVALID_PAGE", "Query parameter 'page' must be an integer");
        }
        if (page < 1) {
            throw ApiException.badRequest("INVALID_PAGE", "Query parameter 'page' must be >= 1");
        }
        return page;
    }

    private int parsePageSize(String raw) {
        if (raw == null || raw.isBlank()) {
            return Math.min(10, this.settings.maxPageSize());
        }
        int pageSize;
        try {
            pageSize = Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            throw ApiException.badRequest("INVALID_PAGE_SIZE", "Query parameter 'pageSize' must be an integer");
        }
        if (pageSize < 1) {
            throw ApiException.badRequest("INVALID_PAGE_SIZE", "Query parameter 'pageSize' must be >= 1");
        }
        return Math.min(pageSize, this.settings.maxPageSize());
    }

    private @NotNull PlayerRegionsResponse handleOwned(@NotNull PlayerRef player, @NotNull UUID playerId,
                                                         int page, int pageSize, int offset) {
        RealtyBackend.SingleCategoryResult result = this.backend.listOwnedRegions(playerId, pageSize, offset);
        List<Object> regions = new ArrayList<>();
        for (RealtyRegionEntity entity : result.regions()) {
            regions.add(toRegionRef(entity));
        }
        return new PlayerRegionsResponse(
                player, page, pageSize, result.totalCount(), totalPages(result.totalCount(), pageSize),
                null, null, null, regions);
    }

    private @NotNull PlayerRegionsResponse handleRented(@NotNull PlayerRef player, @NotNull UUID playerId,
                                                          int page, int pageSize, int offset) {
        RealtyBackend.SingleCategoryResult result = this.backend.listRentedRegions(playerId, pageSize, offset);
        List<RentedRegionView> views = selectRentedWithEndDate(playerId, pageSize, offset);
        List<Object> regions = new ArrayList<>();
        for (RentedRegionView view : views) {
            regions.add(toRentedRef(view));
        }
        return new PlayerRegionsResponse(
                player, page, pageSize, result.totalCount(), totalPages(result.totalCount(), pageSize),
                null, null, null, regions);
    }

    private @NotNull PlayerRegionsResponse handleAll(@NotNull PlayerRef player, @NotNull UUID playerId,
                                                       int page, int pageSize, int offset) {
        RealtyBackend.ListResult result = this.backend.listRegions(playerId, pageSize, offset);

        List<PlayerRegionsResponse.RegionRef> owned = new ArrayList<>();
        for (RealtyRegionEntity entity : result.owned()) {
            owned.add(toRegionRef(entity));
        }
        List<PlayerRegionsResponse.RegionRef> landlord = new ArrayList<>();
        for (RealtyRegionEntity entity : result.landlord()) {
            landlord.add(toRegionRef(entity));
        }

        // Mirror RealtyBackendImpl#listRegions' own pagination arithmetic so the
        // rented slice requested here lines up with the rented slice ListResult
        // already accounted for in its counts, without an N+1 lookup per region.
        int remaining = pageSize - result.owned().size();
        int rentedOffset = Math.max(0, offset - result.ownedCount());
        remaining -= result.landlord().size();
        rentedOffset = Math.max(0, rentedOffset - result.landlordCount());

        List<PlayerRegionsResponse.RentedRef> rented = new ArrayList<>();
        if (remaining > 0) {
            for (RentedRegionView view : selectRentedWithEndDate(playerId, remaining, rentedOffset)) {
                rented.add(toRentedRef(view));
            }
        }

        return new PlayerRegionsResponse(
                player, page, pageSize, result.totalCount(), totalPages(result.totalCount(), pageSize),
                owned, landlord, rented, null);
    }

    private @NotNull List<RentedRegionView> selectRentedWithEndDate(@NotNull UUID playerId, int limit, int offset) {
        try (SqlSessionWrapper session = this.database.openSession(true)) {
            return session.leaseholdContractMapper().selectRentedRegionsWithEndDate(playerId, limit, offset);
        }
    }

    private @NotNull PlayerRegionsResponse.RegionRef toRegionRef(@NotNull RealtyRegionEntity entity) {
        WorldRef world = this.worldLookup.refFor(entity.worldId());
        return new PlayerRegionsResponse.RegionRef(entity.worldGuardRegionId(), world);
    }

    private @NotNull PlayerRegionsResponse.RentedRef toRentedRef(@NotNull RentedRegionView view) {
        WorldRef world = this.worldLookup.refFor(view.worldId());
        LocalDateTime endDate = view.endDate();
        String endDateStr = endDate == null ? null : IsoDates.format(endDate);
        Long secondsRemaining = null;
        if (endDate != null) {
            long seconds = Duration.between(LocalDateTime.now(ZoneOffset.UTC), endDate).toSeconds();
            secondsRemaining = Math.max(0L, seconds);
        }
        return new PlayerRegionsResponse.RentedRef(view.worldGuardRegionId(), world, endDateStr, secondsRemaining);
    }

    private static int totalPages(int totalCount, int pageSize) {
        return (totalCount + pageSize - 1) / pageSize;
    }

}
