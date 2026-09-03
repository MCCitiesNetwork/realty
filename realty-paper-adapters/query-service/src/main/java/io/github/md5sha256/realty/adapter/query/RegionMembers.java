package io.github.md5sha256.realty.adapter.query;

import com.sk89q.worldguard.domains.DefaultDomain;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A region's two WorldGuard domains, as WorldGuard actually holds them.
 *
 * <p>Each domain is three lists rather than one, because WorldGuard stores three kinds of entry
 * and flattening them would lose information a consumer needs. {@code playerIds} are the modern
 * UUID entries; {@code playerNames} are legacy name entries, which carry no id and which
 * WorldGuard lower-cases on the way in; {@code groups} are permission groups, which are not
 * players at all. A region configured the legacy way would otherwise report an empty owner
 * list, which is a worse answer than an honest one.</p>
 */
public record RegionMembers(@NotNull Party owners, @NotNull Party members) {

    public record Party(@NotNull List<String> playerIds,
                        @NotNull List<String> playerNames,
                        @NotNull List<String> groups) {
    }

    /** Must be called on the main thread: {@link ProtectedRegion} is not thread-safe. */
    public static @NotNull RegionMembers fromProtectedRegion(@NotNull ProtectedRegion region) {
        return new RegionMembers(party(region.getOwners()), party(region.getMembers()));
    }

    private static @NotNull Party party(@NotNull DefaultDomain domain) {
        List<String> ids = new ArrayList<>();
        for (UUID id : domain.getUniqueIds()) {
            ids.add(id.toString());
        }
        return new Party(List.copyOf(ids), List.copyOf(domain.getPlayers()),
                List.copyOf(domain.getGroups()));
    }
}
