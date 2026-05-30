package io.github.md5sha256.realty.tax;

import io.github.md5sha256.realty.settings.TagMatch;
import io.github.md5sha256.realty.settings.TaxRule;
import io.github.md5sha256.realty.settings.TaxSettings;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

class PropertyTaxPolicyTest {

    private static final Logger LOG = Logger.getLogger("test");

    private PropertyTaxPolicy policy;

    private static TaxRule rule(TagMatch match, String formula) {
        return new TaxRule(match, formula);
    }

    @BeforeEach
    void setUp() {
        // Mirrors the documented example ruleset.
        TaxSettings settings = new TaxSettings(
                true, "DCGovernment", List.of(), 3,
                List.of(
                        rule(new TagMatch(List.of("commercial", "industrial"), null), "25"),
                        rule(new TagMatch(List.of("residential", "waterfront"), null), "12 * <plots>"),
                        rule(new TagMatch(null, List.of("commercial")), "10"),
                        rule(new TagMatch(List.of("residential"), null), "2 * <plots>^2 - 4 * <plots>")
                ),
                "2.5 * <plots>^2 - 6 * <plots>"
        );
        policy = PropertyTaxPolicy.compile(settings, LOG);
    }

    private void assertTax(String expected, Set<String> tags, int plots) {
        Assertions.assertEquals(new BigDecimal(expected), policy.taxForRegion(tags, plots));
    }

    @Test
    @DisplayName("all: [commercial, industrial] -> flat 25")
    void commercialIndustrialFlat() {
        assertTax("25.00", Set.of("commercial", "industrial"), 7);
        assertTax("25.00", Set.of("commercial", "industrial", "waterfront"), 100);
    }

    @Test
    @DisplayName("first matching rule wins (top-to-bottom)")
    void firstMatchWins() {
        // {commercial, residential}: rule 1 (needs industrial) no; rule 2 (needs waterfront) no;
        // rule 3 (any commercial) yes -> 10, before the residential rule.
        assertTax("10.00", Set.of("commercial", "residential"), 5);
    }

    @Test
    @DisplayName("residential + waterfront -> 12 * plots")
    void residentialWaterfront() {
        assertTax("60.00", Set.of("residential", "waterfront"), 5);
    }

    @Test
    @DisplayName("residential -> progressive on total plots")
    void residentialProgressive() {
        // 2*5^2 - 4*5 = 30
        assertTax("30.00", Set.of("residential"), 5);
    }

    @Test
    @DisplayName("no matching tags -> default formula")
    void untaggedUsesDefault() {
        // 2.5*5^2 - 6*5 = 32.5
        assertTax("32.50", Set.of(), 5);
        assertTax("32.50", Set.of("park"), 5);
    }

    @Test
    @DisplayName("tag matching is case-insensitive")
    void caseInsensitive() {
        assertTax("25.00", Set.of("COMMERCIAL", "Industrial"), 7);
    }

    @Test
    @DisplayName("non-positive results clamp to zero")
    void clampsNegative() {
        // residential at plots=1 -> 2 - 4 = -2 -> 0
        assertTax("0", Set.of("residential"), 1);
    }

    @Test
    @DisplayName("a bad rule formula is dropped, falling through to later rules / default")
    void badRuleFormulaDropped() {
        TaxSettings settings = new TaxSettings(
                true, "DCGovernment", List.of(), 3,
                List.of(rule(new TagMatch(List.of("residential"), null), "2 * acres")),
                "7 * <plots>"
        );
        PropertyTaxPolicy p = PropertyTaxPolicy.compile(settings, LOG);
        // The residential rule is invalid and skipped -> default 7 * plots.
        Assertions.assertEquals(new BigDecimal("21.00"), p.taxForRegion(Set.of("residential"), 3));
    }

    @Test
    @DisplayName("an invalid default-formula falls back to the built-in")
    void invalidDefaultFallsBack() {
        TaxSettings settings = new TaxSettings(
                true, "DCGovernment", List.of(), 3, List.of(), "bogus(("
        );
        PropertyTaxPolicy p = PropertyTaxPolicy.compile(settings, LOG);
        // Built-in default 2.5*x^2 - 6*x at x=5 = 32.5
        Assertions.assertEquals(new BigDecimal("32.50"), p.taxForRegion(Set.of(), 5));
    }
}
