package io.github.md5sha256.realty.rest.json;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * One resource pack a browser-side renderer should texture previews with.
 *
 * @param url         where the browser fetches this pack
 * @param attribution credits for <em>this</em> pack, to render wherever its textures are
 *                    used. Per pack rather than per server: two packs may be licensed
 *                    differently, and a credit on the wrong one credits the wrong author
 */
public record ResourcePackEntry(@NotNull String url,
                                @NotNull List<ResourcePackAttribution> attribution) {

    public ResourcePackEntry {
        attribution = List.copyOf(attribution);
    }
}
