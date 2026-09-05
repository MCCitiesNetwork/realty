package io.github.md5sha256.realty.adapter.query;

import io.github.md5sha256.realty.adapter.query.json.ResourcePackEntry;
import io.github.md5sha256.realty.adapter.query.json.ResourcePackResponse;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Reports the packs named by the module's {@code resource-packs} setting, in the order
 * the operator wrote them.
 *
 * <p>Deliberately not {@code server.properties}' {@code resource-pack}. That one is sent to
 * the game client, which applies it over its own copy of the game, so it is usually an
 * override pack -- a browser has no vanilla assets to override, and would render most blocks
 * with nothing at all. Listing a base pack beneath the override is what supplies them, and
 * is why this is a list rather than the single URL it began as.</p>
 *
 * <p>The URLs alone are configured. Realty never hosts or serves a pack, so it redistributes
 * nothing; the browser fetches each from wherever the operator already publishes it.</p>
 */
public record ConfiguredResourcePackSource(@NotNull List<ResourcePackEntry> packs)
        implements ResourcePackSource {

    public ConfiguredResourcePackSource {
        packs = List.copyOf(packs);
    }

    @Override
    public @NotNull ResourcePackResponse current() {
        // No hash: unlike the game client, a browser has nothing to verify it against, and
        // reporting one the operator never supplied would be a fiction.
        return new ResourcePackResponse(this.packs, null, false);
    }
}
