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
 * The property tax is the rules list and nothing else. Each plot falls to the first
 * matching rule; that rule's formula is charged once per owner on the number of the
 * owner's plots in it, above the rule's exemption, floored to the cent. Plots
 * matching no rule are untaxed.
 *
 * <p>Fixtures use the zoning tags shipped in region-tags.yml plus a couple of
 * hypothetical district tags, since combining a district with a zoning list is the
 * shape servers configure most often.
 */
class PropertyTaxPolicyTest {

    private static final Logger LOG = Logger.getLogger("test");

    private static final List<String> ZONING = List.of("residential", "commercial", "industrial");

    /** A district's zoned land: downtown AND (residential OR commercial OR industrial). */
    private static final TagMatch DOWNTOWN_ZONED = new TagMatch(List.of("downtown"), ZONING);

    /** The formula shipped in taxes.yml: y = 2.7d + 2.87d^2 + 0.0462d^3, d = plots - 2. */
    private static final String CUBIC =
            "2.7 * (<plots> - 2) + 2.87 * (<plots> - 2)^2 + 0.0462 * (<plots> - 2)^3";

    private static PropertyTaxPolicy policy(TaxRule... rules) {
        return PropertyTaxPolicy.compile(
                new TaxSettings(true, "DCGovernment", List.of(), List.of(rules)), LOG);
    }

    /** One rule: the district's zoned land on the cubic, exempt at 2. */
    private static PropertyTaxPolicy districtPolicy() {
        return policy(new TaxRule(DOWNTOWN_ZONED, CUBIC, 2));
    }

    /** N plots all carrying the same tags. */
    private static List<Set<String>> repeat(Set<String> tags, int n) {
        List<Set<String>> plots = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            plots.add(tags);
        }
        return plots;
    }

    private static BigDecimal cubic(int plots) {
        double d = plots - 2;
        double y = 2.7 * d + 2.87 * d * d + 0.0462 * d * d * d;
        return y <= 0 ? BigDecimal.ZERO : BigDecimal.valueOf(y).setScale(2, RoundingMode.FLOOR);
    }

    // ------------------------------------------------------------------
    // Scoping: a rule combining all + any
    // ------------------------------------------------------------------

    @Test
    @DisplayName("only plots matching the rule are taxed or counted")
    void taxesOnlyMatchingPlots() {
        List<Set<String>> plots = new ArrayList<>(repeat(Set.of("downtown", "commercial"), 3));
        plots.addAll(repeat(Set.of("riverside", "residential"), 10)); // another district — no rule
        plots.addAll(repeat(Set.of("downtown", "farmland"), 5));      // right district, unlisted zoning

        // <plots> = 3, not 18: floor(f(3)) = 5.61.
        Assertions.assertEquals(new BigDecimal("5.61"), districtPolicy().taxForOwner(plots));
        Assertions.assertEquals(3, districtPolicy().taxablePlotCount(plots));
    }

    @Test
    @DisplayName("all + any means AND: district alone or zoning alone does not qualify")
    void requiresDistrictAndZoning() {
        PropertyTaxPolicy policy = districtPolicy();
        Assertions.assertEquals(BigDecimal.ZERO,
                policy.taxForOwner(repeat(Set.of("downtown", "farmland"), 10)));
        Assertions.assertEquals(BigDecimal.ZERO,
                policy.taxForOwner(repeat(Set.of("riverside", "commercial"), 10)));
    }

    @Test
    @DisplayName("every tag in the any list satisfies the OR arm")
    void anyListedTagQualifies() {
        PropertyTaxPolicy policy = districtPolicy();
        for (String zoning : ZONING) {
            Assertions.assertEquals(cubic(5), policy.taxForOwner(repeat(Set.of("downtown", zoning), 5)),
                    zoning + " should be taxable");
        }
    }

    @Test
    @DisplayName("tag matching is case-insensitive")
    void matchingIsCaseInsensitive() {
        Assertions.assertEquals(cubic(4), districtPolicy().taxForOwner(repeat(Set.of("Downtown", "COMMERCIAL"), 4)));
    }

    // ------------------------------------------------------------------
    // Charging: once per owner, on the rule's own count
    // ------------------------------------------------------------------

    @Test
    @DisplayName("owners of 2 or fewer plots in the rule are exempt, however much else they own")
    void exemptsTwoOrFewerMatchingPlots() {
        PropertyTaxPolicy policy = districtPolicy();
        for (int n = 0; n <= 2; n++) {
            List<Set<String>> plots = new ArrayList<>(repeat(Set.of("downtown", "commercial"), n));
            plots.addAll(repeat(Set.of("riverside", "commercial"), 50));
            Assertions.assertEquals(BigDecimal.ZERO, policy.taxForOwner(plots),
                    "owner of " + n + " taxable plots should be exempt");
        }
    }

    @Test
    @DisplayName("the formula is evaluated ONCE on the rule's plot count, floored")
    void chargedOncePerOwner() {
        PropertyTaxPolicy policy = districtPolicy();
        Assertions.assertEquals(new BigDecimal("5.61"), policy.taxForOwner(repeat(Set.of("downtown", "commercial"), 3)));
        Assertions.assertEquals(new BigDecimal("35.17"), policy.taxForOwner(repeat(Set.of("downtown", "commercial"), 5)));
        Assertions.assertEquals(new BigDecimal("228.93"), policy.taxForOwner(repeat(Set.of("downtown", "commercial"), 10)));
        for (int n : new int[]{3, 8, 20}) {
            List<Set<String>> plots = repeat(Set.of("downtown", "industrial"), n);
            Assertions.assertEquals(cubic(n), policy.taxForOwner(plots));
            // Guard against per-property summation, which would be n× larger.
            Assertions.assertNotEquals(cubic(n).multiply(BigDecimal.valueOf(n)), policy.taxForOwner(plots));
        }
    }

    @Test
    @DisplayName("tax is rounded DOWN to the nearest cent")
    void roundsDown() {
        // f(3) = 5.6162… -> 5.61, not 5.62.
        Assertions.assertEquals(new BigDecimal("5.61"),
                districtPolicy().taxForOwner(repeat(Set.of("downtown", "commercial"), 3)));
    }

    @Test
    @DisplayName("a negative formula result never becomes a credit")
    void negativeResultIsZero() {
        Assertions.assertEquals(BigDecimal.ZERO,
                policy(new TaxRule(null, "<plots> - 100", 0)).taxForOwner(repeat(Set.of(), 5)));
    }

    // ------------------------------------------------------------------
    // Rule mechanics
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a rule with no match is a server-wide tax")
    void catchAllRuleTaxesEveryPlot() {
        // A progressive server-wide curve, exempt at 7 plots.
        PropertyTaxPolicy policy = policy(new TaxRule(
                null, "0.25 * 1.16^<plots> + 0.3 * <plots>^2 + 2.5 * <plots> - 25", 7));
        Assertions.assertEquals(BigDecimal.ZERO, policy.taxForOwner(repeat(Set.of("residential"), 7)));
        Assertions.assertEquals(new BigDecimal("15.01"), policy.taxForOwner(repeat(Set.of("residential"), 8)));
        Assertions.assertEquals(new BigDecimal("149.86"), policy.taxForOwner(repeat(Set.of(), 20)));
    }

    @Test
    @DisplayName("each rule is counted and charged independently, then summed")
    void rulesAreIndependentBuckets() {
        PropertyTaxPolicy policy = policy(
                new TaxRule(new TagMatch(List.of("commercial"), null), "100 * <plots>", 0),
                new TaxRule(new TagMatch(List.of("industrial"), null), "7 * <plots>", 2));

        List<Set<String>> plots = new ArrayList<>(repeat(Set.of("commercial"), 2));
        plots.addAll(repeat(Set.of("industrial"), 4));
        plots.addAll(repeat(Set.of("residential"), 9)); // no rule — invisible

        // 100*2 (commercial bucket) + 7*4 (industrial bucket, over its threshold of 2) = 228.
        Assertions.assertEquals(new BigDecimal("228.00"), policy.taxForOwner(plots));
        Assertions.assertEquals(6, policy.taxablePlotCount(plots));
    }

    @Test
    @DisplayName("a rule's exemption applies to its own bucket only")
    void exemptionIsPerRule() {
        PropertyTaxPolicy policy = policy(
                new TaxRule(new TagMatch(List.of("commercial"), null), "100 * <plots>", 5),
                new TaxRule(new TagMatch(List.of("industrial"), null), "7 * <plots>", 0));

        List<Set<String>> plots = new ArrayList<>(repeat(Set.of("commercial"), 4)); // under its threshold
        plots.addAll(repeat(Set.of("industrial"), 3));                              // no exemption
        Assertions.assertEquals(new BigDecimal("21.00"), policy.taxForOwner(plots));
    }

    @Test
    @DisplayName("a plot matching several rules falls to the first")
    void firstMatchWins() {
        PropertyTaxPolicy policy = policy(
                new TaxRule(new TagMatch(List.of("downtown", "commercial"), null), "1000", 0),
                new TaxRule(new TagMatch(List.of("downtown"), null), "5", 0));
        // Both rules match, but the first claims the plot — charged once, not twice.
        Assertions.assertEquals(new BigDecimal("1000.00"),
                policy.taxForOwner(List.of(Set.of("downtown", "commercial"))));
    }

    @Test
    @DisplayName("a catch-all placed first swallows every plot, leaving later rules dead")
    void catchAllFirstShadowsLaterRules() {
        PropertyTaxPolicy policy = policy(
                new TaxRule(null, "1 * <plots>", 0),
                new TaxRule(new TagMatch(List.of("commercial"), null), "1000 * <plots>", 0));
        // The documented ordering hazard: the commercial rule never sees a plot.
        Assertions.assertEquals(new BigDecimal("3.00"), policy.taxForOwner(repeat(Set.of("commercial"), 3)));
    }

    @Test
    @DisplayName("a flat per-plot rate is expressible as an aggregate")
    void flatPerPlotRate() {
        PropertyTaxPolicy policy = policy(new TaxRule(new TagMatch(null, List.of("commercial")), "10 * <plots>", 0));
        Assertions.assertEquals(new BigDecimal("30.00"), policy.taxForOwner(repeat(Set.of("commercial"), 3)));
    }

    @Test
    @DisplayName("an explicit zero-rate rule exempts its plots from later rules")
    void zeroRateRuleExempts() {
        PropertyTaxPolicy policy = policy(
                new TaxRule(new TagMatch(null, List.of("residential")), "0", 0),
                new TaxRule(null, "50 * <plots>", 0));
        List<Set<String>> plots = new ArrayList<>(repeat(Set.of("residential"), 10));
        plots.addAll(repeat(Set.of("commercial"), 2));
        Assertions.assertEquals(new BigDecimal("100.00"), policy.taxForOwner(plots));
    }

    // ------------------------------------------------------------------
    // Failure handling
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a rule with an invalid formula is dropped; its plots fall to the next match")
    void badFormulaDropsRule() {
        PropertyTaxPolicy policy = policy(
                new TaxRule(new TagMatch(List.of("commercial"), null), "2 * acres", 0),
                new TaxRule(null, "5 * <plots>", 0));
        // The commercial rule never compiles, so its plots fall through to the catch-all.
        Assertions.assertEquals(new BigDecimal("15.00"), policy.taxForOwner(repeat(Set.of("commercial"), 3)));
    }

    @Test
    @DisplayName("an invalid formula with no fallback rule leaves those plots untaxed")
    void badFormulaWithNoFallbackChargesNothing() {
        PropertyTaxPolicy policy = policy(new TaxRule(new TagMatch(List.of("commercial"), null), "bogus((", 0));
        Assertions.assertEquals(BigDecimal.ZERO, policy.taxForOwner(repeat(Set.of("commercial"), 30)));
        Assertions.assertEquals(0, policy.taxablePlotCount(repeat(Set.of("commercial"), 30)));
    }

    @Test
    @DisplayName("no rules means no property tax")
    void noRulesNoTax() {
        Assertions.assertEquals(BigDecimal.ZERO, policy().taxForOwner(repeat(Set.of("commercial"), 100)));
    }
}
