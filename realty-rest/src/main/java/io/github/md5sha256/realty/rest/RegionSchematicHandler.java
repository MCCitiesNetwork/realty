package io.github.md5sha256.realty.rest;

import io.github.md5sha256.realty.api.RealtyBackend;
import io.javalin.http.Context;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * {@code GET /v1/region/schematic?world=...&region=...} -- the raw Sponge Schematic v3
 * bytes captured by {@code /realty schematic capture}, for a browser-side renderer.
 *
 * <p>Served as bytes rather than JSON deliberately: the frontend schematic renderers
 * read an {@code ArrayBuffer} directly, so base64-wrapping it in JSON would cost a
 * third more bytes and a decode step for nothing.</p>
 */
final class RegionSchematicHandler {

    private final RealtyBackend backend;
    private final WorldLookup worldLookup;

    RegionSchematicHandler(@NotNull RealtyBackend backend, @NotNull WorldLookup worldLookup) {
        this.backend = backend;
        this.worldLookup = worldLookup;
    }

    void handle(@NotNull Context ctx) {
        String worldParam = QueryParams.required(ctx, "world");
        String regionParam = QueryParams.required(ctx, "region");

        // Throws WORLD_NOT_FOUND itself for an unknown world.
        UUID worldId = this.worldLookup.resolve(worldParam);
        byte[] schematic = this.backend.getSchematic(regionParam, worldId);

        if (schematic == null) {
            throw ApiException.notFound("SCHEMATIC_NOT_FOUND",
                    "No schematic captured for region '" + regionParam + "' in world '" + worldParam + "'");
        }

        ctx.contentType("application/octet-stream");
        ctx.result(schematic);
    }
}
