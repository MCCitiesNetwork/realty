package io.github.md5sha256.realty.rest.json;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A player identity. The UUID is always present; the name is null until the
 * query-service enrichment client ships, and null thereafter whenever the module
 * is unreachable.
 */
public record PlayerRef(@NotNull String id, @Nullable String name) {
}
