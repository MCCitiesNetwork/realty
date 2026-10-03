package io.github.md5sha256.realty.rest;

import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.api.RealtyBackend;
import io.github.md5sha256.realty.database.Database;
import io.github.md5sha256.realty.database.SqlSessionWrapper;
import io.github.md5sha256.realty.database.entity.RealtyRegionEntity;
import io.github.md5sha256.realty.database.entity.RentedRegionView;
import io.github.md5sha256.realty.rest.json.PartyRef;
import io.github.md5sha256.realty.rest.json.PlayerRegionsResponse;
import io.github.md5sha256.realty.rest.json.WorldRef;
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
 * The paged listing behind {@code GET /v1/players/regions} and
 * {@code GET /v1/parties/{kind}/{id}/regions}: the HTTP form of {@code /realty list}. The two
 * routes only differ in how they find the party, so the categories, the paging and the
 * response are built here once.
 */
final class PartyRegionsListing {

    private final RealtyBackend backend;
    private final Database database;
    private final WorldLookup worldLookup;
    private final RestSettings settings;

    PartyRegionsListing(@NotNull RealtyBackend backend,
                         @NotNull Database database,
                         @NotNull WorldLookup worldLookup,
                         @NotNull RestSettings settings) {
        this.backend = backend;
        this.database = database;
        this.worldLookup = worldLookup;
        this.settings = settings;
    }

    /**
     * Answers {@code ctx} with the regions of {@code party}, reading {@code category},
     * {@code page} and {@code pageSize} from the query. {@code ref} is the party as the
     * response names it.
     */
    void respond(@NotNull Context ctx, @NotNull Party party, @NotNull PartyRef ref) {
        String category = ctx.queryParam("category");
        if (category == null || category.isBlank()) {
            category = "all";
        }

        int page = QueryParams.page(ctx);
        int pageSize = QueryParams.pageSize(ctx, this.settings.maxPageSize());
        int offset = (page - 1) * pageSize;

        PlayerRegionsResponse response = switch (category) {
            case "all" -> handleAll(ref, party, page, pageSize, offset);
            case "owned" -> handleOwned(ref, party, page, pageSize, offset);
            case "rented" -> handleRented(ref, party, page, pageSize, offset);
            default -> throw ApiException.badRequest("INVALID_CATEGORY",
                    "Query parameter 'category' must be one of [all, owned, rented], got '" + category + "'");
        };

        ctx.json(response);
    }

    private @NotNull PlayerRegionsResponse handleOwned(@NotNull PartyRef player, @NotNull Party party,
                                                         int page, int pageSize, int offset) {
        RealtyBackend.SingleCategoryResult result = this.backend.listOwnedRegions(party, pageSize, offset);
        Map<UUID, WorldRef> worlds = resolveWorlds(regionWorldIds(result.regions()));
        List<Object> regions = new ArrayList<>();
        for (RealtyRegionEntity entity : result.regions()) {
            regions.add(toRegionRef(entity, worlds));
        }
        return new PlayerRegionsResponse(
                player, page, pageSize, result.totalCount(), totalPages(result.totalCount(), pageSize),
                null, null, null, regions);
    }

    private @NotNull PlayerRegionsResponse handleRented(@NotNull PartyRef player, @NotNull Party party,
                                                          int page, int pageSize, int offset) {
        RealtyBackend.SingleCategoryResult result = this.backend.listRentedRegions(party, pageSize, offset);
        List<RentedRegionView> views = selectRentedWithEndDate(party, pageSize, offset);
        Map<UUID, WorldRef> worlds = resolveWorlds(rentedWorldIds(views));
        List<Object> regions = new ArrayList<>();
        for (RentedRegionView view : views) {
            regions.add(toRentedRef(view, worlds));
        }
        return new PlayerRegionsResponse(
                player, page, pageSize, result.totalCount(), totalPages(result.totalCount(), pageSize),
                null, null, null, regions);
    }

    private @NotNull PlayerRegionsResponse handleAll(@NotNull PartyRef player, @NotNull Party party,
                                                       int page, int pageSize, int offset) {
        RealtyBackend.ListResult result = this.backend.listRegions(party, pageSize, offset);

        // Mirror RealtyBackendImpl#listRegions' own pagination arithmetic so the
        // rented slice requested here lines up with the rented slice ListResult
        // already accounted for in its counts, without an N+1 lookup per region.
        int remaining = pageSize - result.owned().size();
        int rentedOffset = Math.max(0, offset - result.ownedCount());
        remaining -= result.landlord().size();
        rentedOffset = Math.max(0, rentedOffset - result.landlordCount());

        List<RentedRegionView> rentedViews = remaining > 0
                ? selectRentedWithEndDate(party, remaining, rentedOffset)
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

    /** Only a player rents, so any other party has no rented regions to read. */
    private @NotNull List<RentedRegionView> selectRentedWithEndDate(@NotNull Party party, int limit, int offset) {
        if (!(party instanceof Party.Personal personal)) {
            return List.of();
        }
        UUID playerId = personal.playerUuid();
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
