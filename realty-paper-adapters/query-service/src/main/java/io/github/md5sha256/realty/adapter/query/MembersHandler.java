package io.github.md5sha256.realty.adapter.query;

import io.javalin.http.Context;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** {@code GET /regions/{worldId}/{regionId}/members}. */
final class MembersHandler {

    private final RegionSource source;
    private final Duration timeout;

    MembersHandler(@NotNull RegionSource source, @NotNull Duration timeout) {
        this.source = Objects.requireNonNull(source, "source");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
    }

    void handle(@NotNull Context ctx) {
        UUID worldId = Paths.worldId(ctx);
        String regionId = ctx.pathParam("regionId");
        Optional<RegionMembers> members = Futures.joinWithin(
                this.source.members(worldId, regionId),
                this.timeout,
                ApiException.MAIN_THREAD_TIMEOUT);
        ctx.json(members.orElseThrow(() -> ApiException.notFound("REGION_NOT_FOUND",
                "No region '" + regionId + "' in world " + worldId)));
    }
}
