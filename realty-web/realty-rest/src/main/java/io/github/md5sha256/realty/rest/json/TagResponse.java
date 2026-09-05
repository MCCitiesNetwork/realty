package io.github.md5sha256.realty.rest.json;

import org.jetbrains.annotations.NotNull;

/**
 * One entry of {@code GET /v1/tags} -- a tag id in use, and how many regions carry it.
 *
 * <p>No display name. Those live in the plugin's {@code RealtyTags} config, which this
 * service does not read, and the existing {@code tags} array on a region reports raw ids
 * for the same reason.</p>
 */
public record TagResponse(@NotNull String id, int regionCount) {
}
