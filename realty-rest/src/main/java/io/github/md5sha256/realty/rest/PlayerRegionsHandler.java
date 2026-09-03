package io.github.md5sha256.realty.rest;

import io.github.md5sha256.realty.api.RealtyBackend;
import io.github.md5sha256.realty.database.Database;
import io.github.md5sha256.realty.database.SqlSessionWrapper;
import io.github.md5sha256.realty.database.entity.RealtyRegionEntity;
import io.github.md5sha256.realty.database.entity.RentedRegionView;
import io.github.md5sha256.realty.rest.json.PlayerRef;
import io.github.md5sha256.realty.rest.json.PlayerRegionsResponse;
import io.github.md5sha256.realty.rest.json.WorldRef;
import io.github.md5sha256.realty.rest.module.ModuleClient;
import io.github.md5sha256.realty.rest.module.NameLookup;
import io.github.md5sha256.realty.rest.module.PlayerNames;
import io.javalin.http.Context;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * {@code GET /v1/players/regions?player=...} -- the HTTP form of {@code /realty list}.
 */
final class PlayerRegionsHandler {

    private final RealtyBackend backend;
    private final Database database;
    private final WorldLookup worldLookup;
    private final RestSettings settings;
    private final ModuleClient moduleClient;

    PlayerRegionsHandler(@NotNull RealtyBackend backend,
                         @NotNull Database database,
                         @NotNull WorldLookup worldLookup,
                         @NotNull RestSettings settings,
                         @NotNull ModuleClient moduleClient) {
        this.backend = backend;
        this.database = database;
        this.worldLookup = worldLookup;
        this.settings = settings;
        this.moduleClient = moduleClient;
    }

    void handle(@NotNull Context ctx) {
        String playerParam = QueryParams.required(ctx, "player");
        PlayerRef player = resolvePlayer(playerParam);
        UUID playerId = UUID.fromString(player.id());

        String category = ctx.queryParam("category");
        if (category == null || category.isBlank()) {
            category = "all";
        }

        int page = QueryParams.page(ctx);
        int pageSize = QueryParams.pageSize(ctx, this.settings.maxPageSize());
        int offset = (page - 1) * pageSize;

        PlayerRegionsResponse response = switch (category) {
            case "all" -> handleAll(player, playerId, page, pageSize, offset);
            case "owned" -> handleOwned(player, playerId, page, pageSize, offset);
            case "rented" -> handleRented(player, playerId, page, pageSize, offset);
            default -> throw ApiException.badRequest("INVALID_CATEGORY",
                    "Query parameter 'category' must be one of [all, owned, rented], got '" + category + "'");
        };

        ctx.json(response);
    }

    /**
     * Determines whether {@code param} should be resolved as a UUID or a player
     * name. A UUID-shaped value (36 characters, hyphens at the standard positions)
     * that fails to parse is treated as a malformed UUID and rejected with 400; a
     * name resolves through the query-service module -- unknown is 404, and an
     * unreachable module is 502.
     */
    private @NotNull PlayerRef resolvePlayer(@NotNull String param) {
        if (isUuidShaped(param)) {
            UUID id;
            try {
                id = UUID.fromString(param);
            } catch (IllegalArgumentException ex) {
                throw ApiException.badRequest("MALFORMED_UUID", "Query parameter 'player' is not a valid UUID");
            }
            return PlayerNames.ref(id, PlayerNames.resolve(this.moduleClient, List.of(id)));
        }
        return switch (this.moduleClient.uuidOf(param)) {
            case NameLookup.Resolved resolved -> new PlayerRef(resolved.id().toString(), resolved.name());
            case NameLookup.Unknown unknown -> throw ApiException.notFound("PLAYER_NOT_FOUND",
                    "No player named '" + param + "'");
            case NameLookup.Unavailable unavailable -> throw ApiException.badGateway("NAME_LOOKUP_UNAVAILABLE",
                    "Player name lookup requires the query-service module, which is not reachable");
        };
    }

    private static boolean isUuidShaped(@NotNull String value) {
        return value.length() == 36
                && value.charAt(8) == '-'
                && value.charAt(13) == '-'
                && value.charAt(18) == '-'
                && value.charAt(23) == '-';
    }

    private @NotNull PlayerRegionsResponse handleOwned(@NotNull PlayerRef player, @NotNull UUID playerId,
                                                         int page, int pageSize, int offset) {
        RealtyBackend.SingleCategoryResult result = this.backend.listOwnedRegions(playerId, pageSize, offset);
        Map<UUID, WorldRef> worlds = resolveWorlds(regionWorldIds(result.regions()));
        List<Object> regions = new ArrayList<>();
        for (RealtyRegionEntity entity : result.regions()) {
            regions.add(toRegionRef(entity, worlds));
        }
        return new PlayerRegionsResponse(
                player, page, pageSize, result.totalCount(), totalPages(result.totalCount(), pageSize),
                null, null, null, regions);
    }

    private @NotNull PlayerRegionsResponse handleRented(@NotNull PlayerRef player, @NotNull UUID playerId,
                                                          int page, int pageSize, int offset) {
        RealtyBackend.SingleCategoryResult result = this.backend.listRentedRegions(playerId, pageSize, offset);
        List<RentedRegionView> views = selectRentedWithEndDate(playerId, pageSize, offset);
        Map<UUID, WorldRef> worlds = resolveWorlds(rentedWorldIds(views));
        List<Object> regions = new ArrayList<>();
        for (RentedRegionView view : views) {
            regions.add(toRentedRef(view, worlds));
        }
        return new PlayerRegionsResponse(
                player, page, pageSize, result.totalCount(), totalPages(result.totalCount(), pageSize),
                null, null, null, regions);
    }

    private @NotNull PlayerRegionsResponse handleAll(@NotNull PlayerRef player, @NotNull UUID playerId,
                                                       int page, int pageSize, int offset) {
        RealtyBackend.ListResult result = this.backend.listRegions(playerId, pageSize, offset);

        // Mirror RealtyBackendImpl#listRegions' own pagination arithmetic so the
        // rented slice requested here lines up with the rented slice ListResult
        // already accounted for in its counts, without an N+1 lookup per region.
        int remaining = pageSize - result.owned().size();
        int rentedOffset = Math.max(0, offset - result.ownedCount());
        remaining -= result.landlord().size();
        rentedOffset = Math.max(0, rentedOffset - result.landlordCount());

        List<RentedRegionView> rentedViews = remaining > 0
                ? selectRentedWithEndDate(playerId, remaining, rentedOffset)
                : List.of();

        Set<UUID> worldIds = new HashSet<>();
        worldIds.addAll(regionWorldIds(result.owned()));
        worldIds.addAll(regionWorldIds(result.landlord()));
        worldIds.addAll(rentedWorldIds(rentedViews));
        Map<UUID, WorldRef> worlds = resolveWorlds(worldIds);

        List<PlayerRegionsResponse.RegionRef> owned = new ArrayList<>();
        for (RealtyRegionEntity entity : result.owned()) {
            owned.add(toRegionRef(entity, worlds));
        }
        List<PlayerRegionsResponse.RegionRef> landlord = new ArrayList<>();
        for (RealtyRegionEntity entity : result.landlord()) {
            landlord.add(toRegionRef(entity, worlds));
        }
        List<PlayerRegionsResponse.RentedRef> rented = new ArrayList<>();
        for (RentedRegionView view : rentedViews) {
            rented.add(toRentedRef(view, worlds));
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

    /**
     * Resolves every distinct world referenced by this request's regions in a
     * single {@link WorldLookup#refsFor} call, so a response listing many regions
     * still opens exactly one session for world resolution.
     */
    private @NotNull Map<UUID, WorldRef> resolveWorlds(@NotNull Set<UUID> worldIds) {
        return this.worldLookup.refsFor(worldIds);
    }

    private static @NotNull Set<UUID> regionWorldIds(@NotNull List<RealtyRegionEntity> entities) {
        Set<UUID> ids = new HashSet<>();
        for (RealtyRegionEntity entity : entities) {
            ids.add(entity.worldId());
        }
        return ids;
    }

    private static @NotNull Set<UUID> rentedWorldIds(@NotNull List<RentedRegionView> views) {
        Set<UUID> ids = new HashSet<>();
        for (RentedRegionView view : views) {
            ids.add(view.worldId());
        }
        return ids;
    }

    private static @NotNull PlayerRegionsResponse.RegionRef toRegionRef(@NotNull RealtyRegionEntity entity,
                                                                          @NotNull Map<UUID, WorldRef> worlds) {
        WorldRef world = worlds.get(entity.worldId());
        return new PlayerRegionsResponse.RegionRef(entity.worldGuardRegionId(), world);
    }

    private static @NotNull PlayerRegionsResponse.RentedRef toRentedRef(@NotNull RentedRegionView view,
                                                                          @NotNull Map<UUID, WorldRef> worlds) {
        WorldRef world = worlds.get(view.worldId());
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
