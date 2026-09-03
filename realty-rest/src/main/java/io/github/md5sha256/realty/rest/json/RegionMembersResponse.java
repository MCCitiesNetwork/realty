package io.github.md5sha256.realty.rest.json;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A region's WorldGuard owner and member lists.
 *
 * <p>Each domain keeps WorldGuard's three kinds of entry apart. {@code players} are the UUID
 * entries, named where the module could name them; {@code playerNames} are legacy name entries,
 * which carry no id at all and so cannot be a {@link PlayerRef}; {@code groups} are permission
 * groups, which are not players. Flattening them would report a legacy-configured region as
 * having no owners.</p>
 */
public record RegionMembersResponse(@NotNull Party owners, @NotNull Party members) {

    public record Party(@NotNull List<PlayerRef> players,
                        @NotNull List<String> playerNames,
                        @NotNull List<String> groups) {
    }
}
