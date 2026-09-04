package io.github.md5sha256.realty.adapter.query.json;

import org.jetbrains.annotations.Nullable;

/**
 * The resource pack this server asks joining clients to download.
 *
 * <p>Only the <em>location</em> is reported, never the pack's bytes. The URL is already
 * public -- every joining player is handed it -- so repeating it redistributes nothing,
 * whereas serving the pack itself from Realty would mean redistributing whatever assets
 * it contains.</p>
 *
 * <p>A {@code null} {@code url} means no pack is configured, which is the default: an
 * empty {@code resource-pack} in {@code server.properties}.</p>
 *
 * @param url      where clients fetch the pack, or {@code null} when none is set
 * @param hash     the SHA-1 the server advertises, or {@code null}
 * @param required whether the server refuses players who decline it
 */
public record ResourcePackResponse(@Nullable String url,
                                   @Nullable String hash,
                                   boolean required) {
}
