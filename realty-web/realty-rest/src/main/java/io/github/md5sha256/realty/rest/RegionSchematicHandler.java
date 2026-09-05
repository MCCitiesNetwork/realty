package io.github.md5sha256.realty.rest;

import io.github.md5sha256.realty.api.RealtyBackend;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import org.jetbrains.annotations.NotNull;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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

        // A capture is replaced in place whenever somebody re-runs the command, so the
        // bytes may change at any time; the browser is told to ask again each visit,
        // and answered with nothing when what it holds is still the current capture.
        String etag = "\"" + sha1Hex(schematic) + "\"";
        ctx.header("ETag", etag);
        ctx.header("Cache-Control", ResponseCaching.REVALIDATE);
        if (etag.equals(ctx.header("If-None-Match"))) {
            ctx.status(HttpStatus.NOT_MODIFIED);
            return;
        }

        ctx.contentType("application/octet-stream");
        ctx.result(schematic);
    }

    private static @NotNull String sha1Hex(byte @NotNull [] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(bytes);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // Every JVM ships SHA-1; a missing one is a broken runtime, not a request error.
            throw new IllegalStateException("SHA-1 is unavailable", e);
        }
    }
}
