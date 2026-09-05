package io.github.md5sha256.realty.adapter.query.json;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/**
 * One resource pack the browser-side renderer should texture previews with.
 *
 * <p>Packs are reported as an ordered list rather than a single URL because a pack is
 * usually authored to sit on top of another. The pack an operator sends to the game
 * client overrides the client's own copy of the game, so on its own in a browser it
 * draws almost nothing; layering it over a pack carrying the base assets is what makes
 * it render. The renderer merges the list, resolving a contested texture in favour of
 * the higher-priority pack.</p>
 *
 * <p><strong>First is highest priority.</strong> That is the order an operator writes
 * their overrides in -- the specific pack first, the base underneath -- and it matches
 * how the rest of this list reads top to bottom.</p>
 *
 * <p>Only the location travels, never the pack's bytes. The URL is already public, since
 * every joining player is handed it, so repeating it redistributes nothing.</p>
 *
 * @param url         where the browser fetches this pack
 * @param attribution credits for <em>this</em> pack, empty when the operator sets none.
 *                    Per pack rather than per server: two packs may be licensed
 *                    differently, and a credit on the wrong one credits the wrong author
 */
public record ResourcePackEntry(@NotNull String url,
                                @NotNull List<ResourcePackAttribution> attribution) {

    public ResourcePackEntry {
        Objects.requireNonNull(url, "url");
        attribution = List.copyOf(attribution);
    }
}
