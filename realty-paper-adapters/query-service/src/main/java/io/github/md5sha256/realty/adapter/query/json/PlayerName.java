package io.github.md5sha256.realty.adapter.query.json;

import org.jetbrains.annotations.Nullable;

/** One identity. Either side may be null when unresolved; neither is omitted. */
public record PlayerName(@Nullable String id, @Nullable String name) {
}
