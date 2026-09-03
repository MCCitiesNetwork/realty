package io.github.md5sha256.realty.adapter.query;

import io.javalin.http.Context;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/** Path-parameter parsing shared by the region routes. */
final class Paths {

    private Paths() {
    }

    static @NotNull UUID worldId(@NotNull Context ctx) {
        String raw = ctx.pathParam("worldId");
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("INVALID_WORLD_ID", "worldId must be a UUID: " + raw);
        }
    }
}
