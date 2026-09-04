package io.github.md5sha256.realty.rest.json;

import org.jetbrains.annotations.Nullable;

/**
 * Where the game server's resource pack is hosted, so a browser renderer can texture blocks
 * with the same pack the game client uses.
 *
 * <p>A location, never the pack's bytes: the URL is already handed to every joining player,
 * so reporting it redistributes nothing, whereas proxying the pack through this service would
 * mean redistributing whatever assets it contains.</p>
 *
 * <p>{@code url} is {@code null} when the server configures no pack, which is the default.
 * That is a normal answer, not an error -- the renderer simply draws untextured geometry.</p>
 */
public record ResourcePackResponse(@Nullable String url,
                                   @Nullable String hash,
                                   boolean required) {
}
