package io.github.md5sha256.realty.rest.module;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * A region's WorldGuard owner and member domains, as the module reports them.
 *
 * <p>Each domain keeps its three kinds of entry separate because WorldGuard does: UUID entries,
 * legacy name entries that carry no id, and permission groups, which are not players at all.</p>
 */
public record RegionMembers(@NotNull Party owners, @NotNull Party members) {

    public record Party(@NotNull List<UUID> playerIds,
                        @NotNull List<String> playerNames,
                        @NotNull List<String> groups) {
    }
}
