package io.github.md5sha256.realty.rest.json;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A player identity. The UUID is always present; the name is null when the module
 * is disabled or unreachable.
 */
public record PlayerRef(@NotNull String id, @Nullable String name) {
}
