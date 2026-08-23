package io.github.md5sha256.realty.settings;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;

/**
 * One property-tax bracket: a {@link TagMatch} predicate paired with the formula
 * charged to the plots it matches.
 *
 * <p>A rule is an <em>aggregate</em> charge, not a per-plot one. Each plot is
 * assigned to the first rule that matches it; the rule's formula is then evaluated
 * <em>once</em> per owner with {@code <plots>} bound to how many of that owner's
 * plots landed in this rule. Owners with {@code exemptThreshold} plots or fewer in
 * the rule pay nothing for it. A plot matching no rule is untaxed.
 *
 * <p>Per-plot rates are expressible as aggregates: a flat $10 per matched plot is
 * {@code "10 * <plots>"}.
 *
 * <p>An omitted {@code match} matches every plot, which is how a server-wide tax
 * is written — as a catch-all rule, placed last.
 */
@ConfigSerializable
public record TaxRule(
        @Setting("match") @Nullable TagMatch match,
        @Setting("formula") @Nullable String formula,
        @Setting("exempt-threshold") int exemptThreshold
) {

    public TaxRule {
        if (match == null) {
            match = new TagMatch(null, null);
        }
        if (formula == null || formula.isBlank()) {
            formula = "0";
        }
        if (exemptThreshold < 0) {
            exemptThreshold = 0;
        }
    }

    public @NotNull TagMatch match() {
        return match;
    }

    public @NotNull String formula() {
        return formula;
    }
}
