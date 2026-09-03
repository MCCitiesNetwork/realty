package io.github.md5sha256.realty.adapter.query;

import io.github.md5sha256.realty.adapter.query.json.RegionsAtResponse;
import io.javalin.http.Context;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code GET /regions/{worldId}/at?x=&z=&y=} -- which regions cover a block.
 *
 * <p>{@code y} is optional and the two forms are different questions, not one with a default:
 * with {@code y} this is a point test at that block, without it a column test over the
 * horizontal footprint at any height. The response names which test ran so the caller need not
 * infer it from whether it remembered to send {@code y}.</p>
 */
final class RegionsAtHandler {

    private final RegionSource source;
    private final Duration timeout;

    RegionsAtHandler(@NotNull RegionSource source, @NotNull Duration timeout) {
        this.source = Objects.requireNonNull(source, "source");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
    }

    void handle(@NotNull Context ctx) {
        UUID worldId = Paths.worldId(ctx);
        int x = requiredCoordinate(ctx, "x");
        int z = requiredCoordinate(ctx, "z");
        Integer y = optionalCoordinate(ctx, "y");

        Optional<List<String>> regions = Futures.joinWithin(
                this.source.regionsAt(worldId, x, y, z),
                this.timeout,
                ApiException.MAIN_THREAD_TIMEOUT);
        List<String> found = regions.orElseThrow(() -> ApiException.notFound("WORLD_NOT_FOUND",
                "No region-managed world " + worldId));
        ctx.json(new RegionsAtResponse(y == null ? "column" : "point", found));
    }

    private static int requiredCoordinate(@NotNull Context ctx, @NotNull String name) {
        String raw = ctx.queryParam(name);
        if (raw == null || raw.isEmpty()) {
            throw ApiException.badRequest("INVALID_COORDINATE",
                    "Query parameter '" + name + "' is required and must be a block coordinate");
        }
        return parse(name, raw);
    }

    /**
     * An absent parameter and an empty one are both "omitted". A client templating the query
     * string with nothing to substitute for {@code y} means the column question, and rejecting
     * that would be a 400 for a well-formed request.
     */
    private static @Nullable Integer optionalCoordinate(@NotNull Context ctx, @NotNull String name) {
        String raw = ctx.queryParam(name);
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        return parse(name, raw);
    }

    private static int parse(@NotNull String name, @NotNull String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            throw ApiException.badRequest("INVALID_COORDINATE",
                    "Query parameter '" + name + "' must be a whole block coordinate: '" + raw + "'");
        }
    }
}
