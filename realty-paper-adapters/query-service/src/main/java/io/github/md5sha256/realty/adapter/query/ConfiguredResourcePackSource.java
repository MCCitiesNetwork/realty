package io.github.md5sha256.realty.adapter.query;

import io.github.md5sha256.realty.adapter.query.json.ResourcePackResponse;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Reports the pack named by the module's {@code resource-pack-url} setting.
 *
 * <p>Deliberately not {@code server.properties}' {@code resource-pack}. That one is sent to
 * the game client, which applies it over its own copy of the game, so it is usually an
 * override pack -- a browser has no vanilla assets to override, and would render most blocks
 * with nothing at all. The preview needs a pack that stands on its own, which is a different
 * choice and so a different setting.</p>
 *
 * <p>The URL alone is configured. Realty never hosts or serves the pack, so it redistributes
 * nothing; the browser fetches it from wherever the operator already publishes it.</p>
 */
public record ConfiguredResourcePackSource(@Nullable String url) implements ResourcePackSource {

    @Override
    public @NotNull ResourcePackResponse current() {
        // No hash: unlike the game client, a browser has nothing to verify it against, and
        // reporting one the operator never supplied would be a fiction.
        return new ResourcePackResponse(this.url, null, false);
    }
}
