package io.github.md5sha256.realty.rest.module;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * One resource pack the game server points browser previews at.
 *
 * <p>A location, never the pack itself. The URL is already public -- every joining player
 * receives it -- so reporting it redistributes nothing.</p>
 *
 * @param url         where the pack is hosted
 * @param attribution credits for this pack; empty when the operator configures none
 */
public record ResourcePackEntry(@NotNull String url,
                                @NotNull List<ResourcePackAttribution> attribution) {

    public ResourcePackEntry {
        attribution = List.copyOf(attribution);
    }
}
