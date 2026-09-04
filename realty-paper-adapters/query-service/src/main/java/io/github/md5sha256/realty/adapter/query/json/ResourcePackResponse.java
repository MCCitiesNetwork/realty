package io.github.md5sha256.realty.adapter.query.json;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The resource pack a browser-side renderer should texture previews with.
 *
 * <p>Only the <em>location</em> is reported, never the pack's bytes. The URL is already
 * public -- every joining player is handed it -- so repeating it redistributes nothing,
 * whereas serving the pack itself from Realty would mean redistributing whatever assets
 * it contains.</p>
 *
 * <p>A {@code null} {@code url} means the operator has configured no pack, which is the
 * default. Named by the module's own {@code resource-pack-url} setting rather than
 * {@code server.properties}: that pack is sent to the game client, which applies it over
 * its own copy of the game, so it is usually an override pack and leaves a browser with
 * nothing underneath it.</p>
 *
 * @param url         where the browser fetches the pack, or {@code null} when none is set
 * @param attribution credits to show wherever the pack's textures are used; empty when
 *                    the operator sets none. Travels with the URL because the operator
 *                    who chooses a pack is the one who knows what its licence asks for
 * @param hash     reserved; always {@code null}, since a browser has nothing to verify against
 * @param required reserved; always {@code false}, since a preview cannot compel a download
 */
public record ResourcePackResponse(@Nullable String url,
                                   @NotNull List<ResourcePackAttribution> attribution,
                                   @Nullable String hash,
                                   boolean required) {
}
