package io.github.md5sha256.realty.adapter.query.json;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Body of {@code POST /regions/{worldId}/dimensions}. */
public record RegionIdsRequest(@Nullable List<String> ids) {
}
