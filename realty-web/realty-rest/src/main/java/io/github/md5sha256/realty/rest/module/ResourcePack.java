package io.github.md5sha256.realty.rest.module;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The resource packs the game server points browser previews at, highest priority first.
 *
 * <p>Locations, never the packs themselves. Each URL is already public -- every joining
 * player receives it -- so reporting it redistributes nothing, whereas serving a pack
 * through Realty would mean redistributing whatever assets it contains.</p>
 *
 * <p>A list rather than one pack because a pack is usually authored to sit on top of
 * another: the renderer merges them, resolving a texture two of them provide in favour of
 * the earlier one.</p>
 *
 * @param packs    every configured pack, highest priority first; empty when none is set
 * @param hash     the SHA-1 the server advertises, or {@code null}
 * @param required whether the server refuses players who decline it
 */
public record ResourcePack(@NotNull List<ResourcePackEntry> packs,
                           @Nullable String hash,
                           boolean required) {

    public ResourcePack {
        packs = List.copyOf(packs);
    }
}
