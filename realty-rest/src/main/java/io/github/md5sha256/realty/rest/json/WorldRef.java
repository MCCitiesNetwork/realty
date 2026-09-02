package io.github.md5sha256.realty.rest.json;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A world identity. The UUID is always present and is the stable identifier; the
 * name is nullable because a region may reference a world the table has never seen.
 */
public record WorldRef(@NotNull String id, @Nullable String name) {
}
