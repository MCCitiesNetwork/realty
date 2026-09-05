package io.github.md5sha256.realty.adapter.query;

import io.javalin.http.Context;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Reports the server's configured resource pack, so a browser-side renderer can texture
 * blocks with the same pack the game client is asked to use.
 *
 * <p>The URL and hash only, never the pack's bytes. That URL is already public -- every
 * joining player is handed it -- so repeating it redistributes nothing, whereas serving the
 * pack from Realty would mean redistributing whatever assets it contains.</p>
 */
final class ResourcePackHandler {

    private final ResourcePackSource source;

    ResourcePackHandler(@NotNull ResourcePackSource source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    void handle(@NotNull Context ctx) {
        ctx.json(this.source.current());
    }
}
