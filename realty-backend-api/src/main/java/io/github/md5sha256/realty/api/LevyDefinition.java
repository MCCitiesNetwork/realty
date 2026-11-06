package io.github.md5sha256.realty.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * A named sale levy.
 *
 * @param name          the levy's name.
 * @param enabled       whether the levy is charged at all.
 * @param isDefault     whether this is the default levy.
 * @param paidBy        who pays the levy.
 * @param recipient     who receives it; {@code null} means the plot's authority.
 * @param authorityKind the kind of authority that may choose this levy, or {@code null} for any.
 * @param rules         the rules, tried in order.
 */
public record LevyDefinition(
        @NotNull String name,
        boolean enabled,
        boolean isDefault,
        @NotNull FeeSide paidBy,
        @Nullable Party recipient,
        @Nullable PartyKind authorityKind,
        @NotNull List<LevyRule> rules
) {

    public LevyDefinition {
        rules = List.copyOf(rules);
    }

    /**
     * @param tags  the region's tags, already lower-cased.
     * @param plots how many plots the seller or buyer holds.
     * @return the percentage from the first matching rule and its tier with the highest
     * {@code fromPlots <= plots}; empty if the levy is disabled, no rule matches or no tier applies.
     */
    public @NotNull OptionalDouble percentFor(@NotNull Set<String> tags, int plots) {
        if (!enabled) {
            return OptionalDouble.empty();
        }
        for (LevyRule rule : rules) {
            if (!rule.matches(tags)) {
                continue;
            }
            LevyTier best = null;
            for (LevyTier tier : rule.tiers()) {
                if (tier.fromPlots() <= plots && (best == null || tier.fromPlots() > best.fromPlots())) {
                    best = tier;
                }
            }
            return best == null ? OptionalDouble.empty() : OptionalDouble.of(best.percent());
        }
        return OptionalDouble.empty();
    }
}
