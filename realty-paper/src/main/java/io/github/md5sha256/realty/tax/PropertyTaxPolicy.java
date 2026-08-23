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
 * Compiled property-tax ruleset. Built once per tax cycle from {@link TaxSettings}.
 *
 * <p>The tax is entirely the {@code rules} list — there is no tax outside it. Each
 * rule pairs a {@link TagMatch} with a formula over {@code <plots>}:
 *
 * <ol>
 *   <li>Every plot the owner title-holds is assigned to the <em>first</em> rule whose
 *       tags it matches. A plot matching no rule is untaxed and counted nowhere.</li>
 *   <li>Each rule's formula is evaluated <em>once</em> per owner, with {@code <plots>}
 *       bound to how many of that owner's plots landed in <em>that</em> rule — not
 *       their total holdings.</li>
 *   <li>A rule charges nothing to an owner at or below its {@code exempt-threshold}.</li>
 *   <li>The rules' charges are summed and rounded <em>down</em> to the cent.</li>
 * </ol>
 *
 * <p>A server-wide tax is therefore a single rule with no {@code match}; a per-city
 * tax is a rule matching that city's tags. Per-plot rates need no separate mode —
 * a flat $10 per matched plot is the aggregate {@code "10 * <plots>"}.
 */
public final class PropertyTaxPolicy {

    private record CompiledRule(TagMatch match, TaxFormula formula, int exemptThreshold) {}

    private final List<CompiledRule> rules;

    private PropertyTaxPolicy(List<CompiledRule> rules) {
        this.rules = rules;
    }

    /**
     * Compiles the configured rules. A rule whose formula does not parse is dropped
     * with a warning — its plots fall through to the next matching rule, or go
     * untaxed — so a typo under-charges rather than charging something unintended.
     */
    public static @NotNull PropertyTaxPolicy compile(@NotNull TaxSettings settings, @NotNull Logger logger) {
        List<CompiledRule> compiled = new ArrayList<>();
        int index = 0;
        for (TaxRule rule : settings.rules()) {
            try {
                compiled.add(new CompiledRule(
                        rule.match(), TaxFormula.compile(rule.formula()), rule.exemptThreshold()));
            } catch (TaxFormulaException e) {
                logger.warning("Ignoring property-tax rule #" + index + " — invalid formula: " + e.getMessage()
                        + " (plots it would have matched are untaxed until this is fixed)");
            }
            index++;
        }
        if (compiled.isEmpty()) {
            logger.warning("No usable property-tax rules configured — no property tax will be charged");
        }
        return new PropertyTaxPolicy(compiled);
    }

    /**
     * Total daily property tax for one owner: the sum of each rule's formula
     * evaluated once on the number of the owner's plots that fell to that rule,
     * rounded down to the cent.
     *
     * @param plotTagSets one tag-set per plot the owner title-holds (tags any case)
     */
    public @NotNull BigDecimal taxForOwner(@NotNull List<Set<String>> plotTagSets) {
        int[] counts = countPlotsPerRule(plotTagSets);

        double raw = 0.0;
        for (int i = 0; i < rules.size(); i++) {
            CompiledRule rule = rules.get(i);
            if (counts[i] <= rule.exemptThreshold()) {
                continue;
            }
            double v = rule.formula().evaluate(counts[i]);
            if (Double.isFinite(v) && v > 0.0) {
                raw += v;
            }
        }

        if (!Double.isFinite(raw) || raw <= 0.0) {
            return BigDecimal.ZERO;
        }
        // Tax is rounded down to the nearest cent.
        return BigDecimal.valueOf(raw).setScale(2, RoundingMode.FLOOR);
    }

    /**
     * How many of the owner's plots fall under any rule — the plots the tax can see.
     * Plots matching no rule are excluded.
     */
    public int taxablePlotCount(@NotNull List<Set<String>> plotTagSets) {
        int total = 0;
        for (int count : countPlotsPerRule(plotTagSets)) {
            total += count;
        }
        return total;
    }

    /** Assigns each plot to the first rule that matches it and tallies the buckets. */
    private int[] countPlotsPerRule(@NotNull List<Set<String>> plotTagSets) {
        int[] counts = new int[rules.size()];
        if (rules.isEmpty()) {
            return counts;
        }
        for (Set<String> plotTags : plotTagSets) {
            Set<String> tags = plotTags.stream()
                    .map(t -> t.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toSet());
            for (int i = 0; i < rules.size(); i++) {
                if (rules.get(i).match().matches(tags)) {
                    counts[i]++;
                    break;
                }
            }
        }
        return counts;
    }
}
