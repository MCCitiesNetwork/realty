package io.github.md5sha256.realty.rest.module;

import org.jetbrains.annotations.Nullable;

/**
 * The resource pack the game server asks joining clients to download.
 *
 * <p>A location, never the pack itself. The URL is already public -- every joining player
 * receives it -- so reporting it redistributes nothing, whereas serving the pack through
 * Realty would mean redistributing whatever assets it contains.</p>
 *
 * @param url      where the pack is hosted, or {@code null} when the server sets none
 * @param hash     the SHA-1 the server advertises, or {@code null}
 * @param required whether the server refuses players who decline it
 */
public record ResourcePack(@Nullable String url, @Nullable String hash, boolean required) {
}
