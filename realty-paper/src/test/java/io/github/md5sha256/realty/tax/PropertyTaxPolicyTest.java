package io.github.md5sha256.realty.tax;

import io.github.md5sha256.realty.settings.TagMatch;
import io.github.md5sha256.realty.settings.TaxRule;
import io.github.md5sha256.realty.settings.TaxSettings;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * The Taxation Act's property tax: a single formula on the owner's plot count,
 * evaluated once (not per plot), rounded down to the cent, exempt at 7 plots or
 * fewer. Tag rules are optional local-government overrides and off by default.
 */
class PropertyTaxPolicyTest {

    private static final Logger LOG = Logger.getLogger("test");
    private static final int ACT_THRESHOLD = 7;

    private static TaxRule rule(TagMatch match, String formula) {
        return new TaxRule(match, formula);
    }

    /** A policy with the built-in (Act) default formula and no override rules. */
    private static PropertyTaxPolicy actPolicy() {
        return PropertyTaxPolicy.compile(
                new TaxSettings(true, "DCGovernment", List.of(), ACT_THRESHOLD, List.of(), TaxSettings.DEFAULT_FORMULA),
                LOG);
    }

    /** N untagged (federal) plots. */
    private static List<Set<String>> untagged(int n) {
        List<Set<String>> plots = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            plots.add(Set.of());
        }
        return plots;
    }

    /** The Act's formula computed directly, once, floored — the expected total. */
    private static BigDecimal actTax(int plots) {
        double y = 0.25 * Math.pow(1.16, plots) + 0.3 * plots * plots + 2.5 * plots - 25;
        return y <= 0 ? BigDecimal.ZERO : BigDecimal.valueOf(y).setScale(2, RoundingMode.FLOOR);
    }

    @Test
    @DisplayName("owners of 7 or fewer plots are exempt")
    void exemptsSevenOrFewer() {
        PropertyTaxPolicy policy = actPolicy();
        for (int n = 0; n <= 7; n++) {
            Assertions.assertEquals(BigDecimal.ZERO, policy.taxForOwner(untagged(n), ACT_THRESHOLD),
                    "owner of " + n + " plots should be exempt");
        }
    }

    @Test
    @DisplayName("matches the Act's published daily figures")
    void matchesPublishedFigures() {
        PropertyTaxPolicy policy = actPolicy();
        // From the Act: ~$15.01 @ 8 plots, ~$149.86 @ 20 plots.
        Assertions.assertEquals(new BigDecimal("15.01"), policy.taxForOwner(untagged(8), ACT_THRESHOLD));
        Assertions.assertEquals(new BigDecimal("149.86"), policy.taxForOwner(untagged(20), ACT_THRESHOLD));
    }

    @Test
    @DisplayName("formula is evaluated ONCE on total plots (not summed per plot)")
    void evaluatedOncePerOwner() {
        PropertyTaxPolicy policy = actPolicy();
        for (int n : new int[]{8, 12, 20, 50}) {
            Assertions.assertEquals(actTax(n), policy.taxForOwner(untagged(n), ACT_THRESHOLD),
                    "tax for " + n + " plots should be floor(formula(" + n + ")), charged once");
            // Guard against the old per-property summation (which would be n× larger).
            Assertions.assertNotEquals(actTax(n).multiply(BigDecimal.valueOf(n)),
                    policy.taxForOwner(untagged(n), ACT_THRESHOLD));
        }
    }

    @Test
    @DisplayName("tax is rounded DOWN to the nearest cent")
    void roundsDown() {
        // floor: 15.0196… -> 15.01 (not 15.02).
        Assertions.assertEquals(new BigDecimal("15.01"), actPolicy().taxForOwner(untagged(8), ACT_THRESHOLD));
    }

    @Test
    @DisplayName("local override: tagged plots use the rule per-property; the rest go federal once")
    void localOverridePlusFederal() {
        // default "7 * <plots>", threshold 0; one commercial plot (flat 10) + two federal.
        TaxSettings settings = new TaxSettings(
                true, "DCGovernment", List.of(), 0,
                List.of(rule(new TagMatch(null, List.of("commercial")), "10")),
                "7 * <plots>");
        PropertyTaxPolicy policy = PropertyTaxPolicy.compile(settings, LOG);

        List<Set<String>> plots = List.of(Set.of("commercial"), Set.of(), Set.of());
        // override 10 (one commercial) + federal 7 * 2 (two federal plots) = 24.
        Assertions.assertEquals(new BigDecimal("24.00"), policy.taxForOwner(plots, 0));
    }

    @Test
    @DisplayName("override tag match is case-insensitive")
    void overrideCaseInsensitive() {
        TaxSettings settings = new TaxSettings(
                true, "DCGovernment", List.of(), 0,
                List.of(rule(new TagMatch(List.of("commercial", "industrial"), null), "25")),
                "0");
        PropertyTaxPolicy policy = PropertyTaxPolicy.compile(settings, LOG);
        Assertions.assertEquals(new BigDecimal("25.00"),
                policy.taxForOwner(List.of(Set.of("COMMERCIAL", "Industrial")), 0));
    }

    @Test
    @DisplayName("a bad rule formula is dropped — that plot falls back to federal")
    void badRuleFormulaDropped() {
        TaxSettings settings = new TaxSettings(
                true, "DCGovernment", List.of(), 0,
                List.of(rule(new TagMatch(List.of("residential"), null), "2 * acres")),
                "7 * <plots>");
        PropertyTaxPolicy policy = PropertyTaxPolicy.compile(settings, LOG);
        // The residential rule is invalid and dropped -> the plot is federal -> 7 * 1.
        Assertions.assertEquals(new BigDecimal("7.00"), policy.taxForOwner(List.of(Set.of("residential")), 0));
    }

    @Test
    @DisplayName("an invalid default-formula falls back to the built-in Act formula")
    void invalidDefaultFallsBack() {
        TaxSettings settings = new TaxSettings(
                true, "DCGovernment", List.of(), ACT_THRESHOLD, List.of(), "bogus((");
        PropertyTaxPolicy policy = PropertyTaxPolicy.compile(settings, LOG);
        Assertions.assertEquals(new BigDecimal("15.01"), policy.taxForOwner(untagged(8), ACT_THRESHOLD));
    }
}
