package io.github.md5sha256.realty.tax;

import io.github.md5sha256.realty.settings.TagMatch;
import io.github.md5sha256.realty.settings.TaxRule;
import io.github.md5sha256.realty.settings.TaxSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
 * <p><b>Federal model (the Taxation Act).</b> The {@code default-formula} gives the
 * owner's <em>total</em> daily tax as a single function of their plot count — it is
 * evaluated <em>once</em> on the federally-taxed plot count and rounded <em>down</em>
 * to the cent, and owners with {@code exemptThreshold} federal plots or fewer pay
 * nothing. With no rules configured (the default) this is exactly the Act:
 * {@code floor(default-formula(totalPlots))}, exempt at/below the threshold.
 *
 * <p><b>Local-government overrides (optional, off by default).</b> A plot whose tags
 * match a rule (first match wins) is taxed by that rule per-property — with
 * {@code <plots>} bound to the owner's total plots — and is excluded from the federal
 * count. This models the Act's "unless otherwise provided by Local Governments"
 * clause; the shipped config defines no rules, so every plot is federal.
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
     * Total daily property tax for one owner.
     *
     * <p>Federal plots (no matching rule) are taxed by the default formula evaluated
     * <em>once</em> on their count; an owner at or below {@code exemptThreshold}
     * federal plots pays no federal tax. Plots matching a rule are instead taxed by
     * that rule per-property, with {@code <plots>} bound to the owner's total plots.
     * The two parts are summed and rounded down to the cent (the Act rounds down).
     *
     * <p>With no rules configured this reduces to {@code floor(default-formula(N))}
     * for N total plots above the threshold — i.e. exactly the Taxation Act.
     *
     * @param plotTagSets one tag-set per plot the owner title-holds (tags any case)
     * @param exemptThreshold federal plots at/below which no federal tax is charged
     */
    public @NotNull BigDecimal taxForOwner(@NotNull List<Set<String>> plotTagSets, int exemptThreshold) {
        int totalPlots = plotTagSets.size();
        int federalPlots = 0;
        double overrideRaw = 0.0;

        for (Set<String> plotTags : plotTagSets) {
            TaxFormula override = matchRule(plotTags);
            if (override == null) {
                federalPlots++;
            } else {
                double v = override.evaluate(totalPlots);
                if (Double.isFinite(v) && v > 0.0) {
                    overrideRaw += v;
                }
            }
        }

        // Federal tax: a single evaluation on the federal plot count (the Act's
        // formula), charged only above the exemption threshold.
        double federalRaw = 0.0;
        if (federalPlots > exemptThreshold) {
            double v = defaultFormula.evaluate(federalPlots);
            if (Double.isFinite(v) && v > 0.0) {
                federalRaw = v;
            }
        }

        double raw = federalRaw + overrideRaw;
        if (!Double.isFinite(raw) || raw <= 0.0) {
            return BigDecimal.ZERO;
        }
        // The Taxation Act rounds tax down to the nearest cent.
        return BigDecimal.valueOf(raw).setScale(2, RoundingMode.FLOOR);
    }

    /** The first rule whose tags match, or {@code null} when the plot is federal. */
    private @Nullable TaxFormula matchRule(@NotNull Set<String> rawTags) {
        if (rules.isEmpty()) {
            return null;
        }
        Set<String> tags = rawTags.stream()
                .map(t -> t.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        for (CompiledRule rule : rules) {
            if (rule.match().matches(tags)) {
                return rule.formula();
            }
        }
        return null;
    }
}
