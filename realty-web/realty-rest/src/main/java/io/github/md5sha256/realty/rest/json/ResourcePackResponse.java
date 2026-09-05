package io.github.md5sha256.realty.rest.json;

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
 * <p>A list rather than one pack because a pack is usually authored to sit on top of
 * another. The pack a server sends to the game client overrides the client's own copy of
 * the game, so alone in a browser it draws almost nothing; a base pack listed beneath it
 * supplies what the client would have had. The renderer merges the list, resolving a
 * contested texture in favour of the earlier pack.</p>
 *
 * @param packs    every configured pack, highest priority first. Empty means the server
 *                 configures none, which is the default
 * @param hash     reserved; always null, since a browser has nothing to verify against
 * @param required reserved; always false, since a preview cannot compel a download
 */
public record ResourcePackResponse(@NotNull List<ResourcePackEntry> packs,
                                   @Nullable String hash,
                                   boolean required) {

    public ResourcePackResponse {
        packs = List.copyOf(packs);
    }
}
