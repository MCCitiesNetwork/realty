package io.github.md5sha256.realty.adapter.query;

import io.javalin.http.Context;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** {@code GET /regions/{worldId}/{regionId}/dimensions}. */
final class DimensionsHandler {

    private final RegionDimensionsSource source;
    private final Duration timeout;

    DimensionsHandler(@NotNull RegionDimensionsSource source, @NotNull Duration timeout) {
        this.source = Objects.requireNonNull(source, "source");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
    }

    void handle(@NotNull Context ctx) {
        UUID worldId;
        try {
            worldId = UUID.fromString(ctx.pathParam("worldId"));
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("INVALID_WORLD_ID",
                    "worldId must be a UUID: " + ctx.pathParam("worldId"));
        }
        String regionId = ctx.pathParam("regionId");
        // The timeout inside joinWithin is load-bearing beyond answering a wedged tick. A module
        // reload is marshalled onto the main thread by ModuleCommandGroup, so javalin.stop() runs
        // there — while this request may be parked waiting for a main-thread task that the reload
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
}
