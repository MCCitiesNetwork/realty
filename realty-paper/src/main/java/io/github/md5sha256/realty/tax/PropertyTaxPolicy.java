package io.github.md5sha256.realty.tax;

import io.github.md5sha256.realty.settings.TagMatch;
import io.github.md5sha256.realty.settings.TaxRule;
import io.github.md5sha256.realty.settings.TaxSettings;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Compiled property-tax ruleset. Built once per tax cycle from {@link TaxSettings}:
 * each rule's {@link TaxFormula} is parsed up front (bad formulas are logged and
 * dropped). For a region, the first rule whose {@link TagMatch} matches its tags
 * wins; if none match, the default formula applies. {@code <plots>} is bound to
 * the owner's total plot count by the caller.
 */
public final class PropertyTaxPolicy {

    private record CompiledRule(TagMatch match, TaxFormula formula) {}

    private final List<CompiledRule> rules;
    private final TaxFormula defaultFormula;

    private PropertyTaxPolicy(List<CompiledRule> rules, TaxFormula defaultFormula) {
        this.rules = rules;
        this.defaultFormula = defaultFormula;
    }

    public static @NotNull PropertyTaxPolicy compile(@NotNull TaxSettings settings, @NotNull Logger logger) {
        List<CompiledRule> compiled = new ArrayList<>();
        int index = 0;
        for (TaxRule rule : settings.rules()) {
            try {
                compiled.add(new CompiledRule(rule.match(), TaxFormula.compile(rule.formula())));
            } catch (TaxFormulaException e) {
                logger.warning("Ignoring property-tax rule #" + index + " — invalid formula: " + e.getMessage());
            }
            index++;
        }

        TaxFormula fallback;
        try {
            fallback = TaxFormula.compile(settings.defaultFormula());
        } catch (TaxFormulaException e) {
            logger.warning("Invalid default-formula '" + settings.defaultFormula()
                    + "' — using built-in default. " + e.getMessage());
            fallback = TaxFormula.compile(TaxSettings.DEFAULT_FORMULA);
        }
        return new PropertyTaxPolicy(compiled, fallback);
    }

    /**
     * Tax for a single region: the first matching rule's formula (else the
     * default), evaluated at {@code totalPlots}. Non-finite or non-positive
     * results clamp to zero; otherwise rounded HALF_UP to 2 dp.
     *
     * @param regionTags the region's tags (any case)
     * @param totalPlots the owner's total plot count, bound to {@code <plots>}
     */
    public @NotNull BigDecimal taxForRegion(@NotNull Set<String> regionTags, int totalPlots) {
        Set<String> tags = regionTags.stream()
                .map(t -> t.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

        TaxFormula formula = defaultFormula;
        for (CompiledRule rule : rules) {
            if (rule.match().matches(tags)) {
                formula = rule.formula();
                break;
            }
        }

        double raw = formula.evaluate(totalPlots);
        if (!Double.isFinite(raw) || raw <= 0.0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(raw).setScale(2, RoundingMode.HALF_UP);
    }
}
