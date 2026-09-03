package io.github.md5sha256.realty.adapter.query;

import io.javalin.http.Context;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
        Optional<RegionDimensions> dims;
        try {
            dims = this.source.dimensions(worldId, regionId)
                    .orTimeout(this.timeout.toMillis(), TimeUnit.MILLISECONDS)
                    .join();
        } catch (CompletionException ex) {
            if (ex.getCause() instanceof TimeoutException) {
                throw ApiException.gatewayTimeout(
                        "Main thread did not answer within " + this.timeout.toMillis() + "ms");
            }
            throw ex;
        }
        ctx.json(dims.orElseThrow(() -> ApiException.notFound("REGION_NOT_FOUND",
                "No region '" + regionId + "' in world " + worldId)));
    }
}
