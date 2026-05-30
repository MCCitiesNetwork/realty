package io.github.md5sha256.realty.settings;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;

/**
 * A single property-tax rule: a {@link TagMatch} predicate plus the formula
 * (a {@link io.github.md5sha256.realty.tax.TaxFormula} expression over
 * {@code <plots>}) applied to regions it matches. Rules are evaluated top-to-
 * bottom and the first match wins.
 */
@ConfigSerializable
public record TaxRule(
        @Setting("match") @Nullable TagMatch match,
        @Setting("formula") @Nullable String formula
) {

    public TaxRule {
        if (match == null) {
            match = new TagMatch(null, null);
        }
        if (formula == null || formula.isBlank()) {
            formula = "0";
        }
    }

    public @NotNull TagMatch match() {
        return match;
    }

    public @NotNull String formula() {
        return formula;
    }
}
