package io.github.md5sha256.realty.adapter.query;

import io.github.md5sha256.realty.adapter.query.json.RegionIdsRequest;
import io.javalin.http.Context;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Region geometry, one region at a time or a page of them at once. */
final class DimensionsHandler {

    private final RegionSource source;
    private final Duration timeout;

    DimensionsHandler(@NotNull RegionSource source, @NotNull Duration timeout) {
        this.source = Objects.requireNonNull(source, "source");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
    }

    /** {@code GET /regions/{worldId}/{regionId}/dimensions}. */
    void handle(@NotNull Context ctx) {
        UUID worldId = Paths.worldId(ctx);
        String regionId = ctx.pathParam("regionId");
        // The timeout inside joinWithin is load-bearing beyond answering a wedged tick. A module
        // reload is marshalled onto the main thread by ModuleCommandGroup, so javalin.stop() runs
        // there - while this request may be parked waiting for a main-thread task that the reload
        // itself is now preventing from running. The timeout fires on the common scheduler, not the
        // main thread, so it is what releases this worker and lets Jetty's drain complete instead of
        // deadlocking the server against its own reload.
        Optional<RegionDimensions> dims = Futures.joinWithin(
                this.source.dimensions(worldId, regionId),
                this.timeout,
                ApiException.MAIN_THREAD_TIMEOUT);
        ctx.json(dims.orElseThrow(() -> ApiException.notFound("REGION_NOT_FOUND",
                "No region '" + regionId + "' in world " + worldId)));
    }

    /**
     * {@code POST /regions/{worldId}/dimensions} -- geometry for many regions in one main-thread
     * hop, so drawing a map overlay costs one request rather than one per region. A body rather
     * than a query string, for the same reason the player batches use one: a WorldGuard region id
     * is not reliably URL-safe.
     *
     * <p>An id naming no region is omitted from the response rather than mapped to null. The
     * caller sent the ids, so it can see which came back; padding the map with nulls would only
     * make an absent region look like a region with no geometry.</p>
     */
    void batch(@NotNull Context ctx) {
        UUID worldId = Paths.worldId(ctx);
        RegionIdsRequest request = Bodies.read(ctx, RegionIdsRequest.class);
        List<String> ids = request.ids();
        if (ids == null) {
            throw ApiException.badRequest("INVALID_BODY", "Body must be {\"ids\":[...]}");
        }
        for (String id : ids) {
            if (id == null) {
                throw ApiException.badRequest("INVALID_BODY", "Ids must not contain null");
            }
        }
        Bodies.requireWithinBatchLimit(ids.size());
        Map<String, RegionDimensions> dims = Futures.joinWithin(
                this.source.dimensionsOf(worldId, ids),
                this.timeout,
                ApiException.MAIN_THREAD_TIMEOUT);
        ctx.json(Map.of("regions", dims));
    }
}
