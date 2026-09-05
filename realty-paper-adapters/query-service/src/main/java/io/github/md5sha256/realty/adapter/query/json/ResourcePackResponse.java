package io.github.md5sha256.realty.adapter.query.json;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The resource packs a browser-side renderer should texture previews with, highest
 * priority first.
 *
 * <p>Only the <em>locations</em> are reported, never any pack's bytes. Each URL is
 * already public -- every joining player is handed it -- so repeating it redistributes
 * nothing, whereas serving a pack from Realty would mean redistributing its assets.</p>
 *
 * <p>An empty {@code packs} means the operator has configured none, which is the default.
 * Named by the module's own settings rather than {@code server.properties}: that pack is
 * sent to the game client, which applies it over its own copy of the game, so it is
 * usually an override pack and leaves a browser with nothing underneath it. Listing
 * several packs is how an operator supplies that missing base.</p>
 *
 * @param packs    every configured pack, highest priority first; the renderer resolves a
 *                 texture two of them both provide in favour of the earlier one
 * @param hash     reserved; always {@code null}, since a browser has nothing to verify against
 * @param required reserved; always {@code false}, since a preview cannot compel a download
 */
public record ResourcePackResponse(@NotNull List<ResourcePackEntry> packs,
                                   @Nullable String hash,
                                   boolean required) {

    public ResourcePackResponse {
        packs = List.copyOf(packs);
    }
}
