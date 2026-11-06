package io.github.md5sha256.realty.api;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A tag predicate with the tiers that apply when it matches.
 *
 * <ul>
 *   <li>{@code all}: the region must carry every listed tag.</li>
 *   <li>{@code any}: the region must carry at least one listed tag.</li>
 * </ul>
 *
 * An empty set imposes no constraint, so a rule with neither matches every region.
 * Tags are lower-cased.
 */
public record LevyRule(
        @NotNull Set<String> all,
        @NotNull Set<String> any,
        @NotNull List<LevyTier> tiers
) {

    public LevyRule {
        all = normalize(all);
        any = normalize(any);
        tiers = List.copyOf(tiers);
    }

    private static Set<String> normalize(Set<String> tags) {
        return tags.stream()
                .filter(t -> t != null && !t.isBlank())
                .map(t -> t.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * @param lowerCasedTags the region's tags, already lower-cased.
     * @return true if this rule's predicate is satisfied.
     */
    public boolean matches(@NotNull Set<String> lowerCasedTags) {
        return lowerCasedTags.containsAll(all)
                && (any.isEmpty() || !Collections.disjoint(any, lowerCasedTags));
    }
}
