package io.github.md5sha256.realty.adapter.query;

import io.github.md5sha256.realty.adapter.query.json.ResourcePackResponse;
import org.jetbrains.annotations.NotNull;

/**
 * The server's configured resource pack.
 *
 * <p>A seam over Bukkit, in the same spirit as {@link RegionSource}: it keeps the handler
 * testable without a running server, and keeps static {@code Bukkit.getServer()} access out
 * of the classes that do the work.</p>
 *
 * <p>Unlike {@link RegionSource} this is synchronous. It reads server configuration rather
 * than world state, so it needs no main-thread hop.</p>
 */
public interface ResourcePackSource {

    /** The configured pack, or one with a {@code null} url when none is set. */
    @NotNull ResourcePackResponse current();
}
