package io.github.md5sha256.realty.rest;

import io.github.md5sha256.realty.database.Database;
import io.github.md5sha256.realty.database.SqlSessionWrapper;
import io.github.md5sha256.realty.rest.json.RegionsAtResponse;
import io.github.md5sha256.realty.rest.json.WorldRef;
import io.github.md5sha256.realty.rest.module.ModuleClient;
import io.github.md5sha256.realty.rest.module.ModuleResult;
import io.github.md5sha256.realty.rest.module.RegionsAt;
import io.javalin.http.Context;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * {@code GET /v1/regions/at?world=&x=&z=&y=} -- which registered regions contain a block.
 *
 * <p>{@code y} is optional, and the two forms ask different questions rather than one with a
 * default. With {@code y} this is a point test at that block, which is what a player standing
 * somewhere means. Without it, a column test over the horizontal footprint at any height, which
 * is what a map click means -- a 2-D map has no {@code y} to send, and it is why the answer is a
 * list: stacked regions in one column all match. Defaulting {@code y} to the build floor or sea
 * level would silently answer the column question wrongly on a server using vertical
 * subdivision, and the caller could not tell from the response which it got. So the response
 * says which test ran.</p>
 *
 * <p>WorldGuard's answer is intersected with {@code RealtyRegion}: a region this plugin does not
 * manage is not this API's to report.</p>
 */
final class RegionsAtHandler {

    private final Database database;
    private final WorldLookup worldLookup;
    private final ModuleClient moduleClient;

    RegionsAtHandler(@NotNull Database database,
                     @NotNull WorldLookup worldLookup,
                     @NotNull ModuleClient moduleClient) {
        this.database = database;
        this.worldLookup = worldLookup;
        this.moduleClient = moduleClient;
    }

    void handle(@NotNull Context ctx) {
        UUID worldId = this.worldLookup.resolve(QueryParams.required(ctx, "world"));
        int x = requiredCoordinate(ctx, "x");
        int z = requiredCoordinate(ctx, "z");
        String rawY = QueryParams.optional(ctx, "y");
        Integer y = rawY == null ? null : parse("y", rawY);

        RegionsAt found = switch (this.moduleClient.regionsAt(worldId, x, y, z)) {
            case ModuleResult.Found<RegionsAt> result -> result.value();
            case ModuleResult.NotFound<RegionsAt> notFound -> throw ApiException.notFound(
                    "WORLD_NOT_FOUND", "The server has no region-managed world " + worldId);
            case ModuleResult.Unavailable<RegionsAt> unavailable -> throw ApiException.badGateway(
                    "GEOMETRY_UNAVAILABLE",
                    "Locating a block requires the query-service module, which is not reachable");
        };

        List<String> registered;
        try (SqlSessionWrapper session = this.database.openSession(true)) {
            registered = session.realtyRegionMapper().selectRegisteredIds(worldId, found.regionIds());
        }
        // Reported in WorldGuard's order rather than the SQL filter's, so a caller sees the
        // regions in the order the server itself applies them.
        WorldRef world = this.worldLookup.refFor(worldId);
        List<RegionsAtResponse.Entry> entries = new ArrayList<>();
        for (String regionId : found.regionIds()) {
            if (registered.contains(regionId)) {
                entries.add(new RegionsAtResponse.Entry(regionId, world));
            }
        }
        ctx.json(new RegionsAtResponse(found.test(), entries));
    }

    private static int requiredCoordinate(@NotNull Context ctx, @NotNull String name) {
        String raw = QueryParams.optional(ctx, name);
        if (raw == null) {
            throw ApiException.badRequest("INVALID_COORDINATE",
                    "Query parameter '" + name + "' is required and must be a block coordinate");
        }
        return parse(name, raw);
    }

    private static int parse(@NotNull String name, @NotNull String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            throw ApiException.badRequest("INVALID_COORDINATE",
                    "Query parameter '" + name + "' must be a whole block coordinate");
        }
    }
}
