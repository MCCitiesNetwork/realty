package io.github.md5sha256.realty.settings;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Tag predicate for a property-tax rule.
 *
 * <ul>
 *   <li>{@code all} — the region must carry <em>every</em> listed tag.</li>
 *   <li>{@code any} — the region must carry <em>at least one</em> listed tag.</li>
 * </ul>
 *
 * Both are optional; an empty/omitted list imposes no constraint, so a TagMatch
 * with neither matches every region (a catch-all). Matching is case-insensitive.
 */
@ConfigSerializable
public record TagMatch(
        @Setting("all") @Nullable List<String> all,
        @Setting("any") @Nullable List<String> any
) {

    public TagMatch {
        all = normalize(all);
        any = normalize(any);
    }

    private static List<String> normalize(@Nullable List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        return tags.stream()
                .filter(t -> t != null && !t.isBlank())
                .map(t -> t.trim().toLowerCase(Locale.ROOT))
                .toList();
    }

    /**
     * @param regionTags the region's tags, already lower-cased.
     * @return true if this predicate is satisfied.
     */
    public boolean matches(@NotNull Set<String> regionTags) {
        if (!all.isEmpty() && !regionTags.containsAll(all)) {
            return false;
        }
        if (!any.isEmpty()) {
            boolean hit = false;
            for (String tag : any) {
                if (regionTags.contains(tag)) {
                    hit = true;
                    break;
                }
            }
            return hit;
        }
        return true;
    }
}
