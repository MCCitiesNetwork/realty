package io.github.md5sha256.realty.settings;

import io.github.md5sha256.realty.tax.PropertyTaxPolicy;
import io.github.md5sha256.realty.tax.TaxFormula;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Guards the shipped taxes.yml. The tax is now entirely the {@code rules} list, so a
 * key renamed in {@link TaxSettings} or a typo in the resource stops collection
 * silently — the plugin just logs a warning and charges nobody. These tests load the
 * packaged resource exactly as the plugin does and assert it still means what its
 * comments say.
 */
class TaxesConfigTest {

    private static final Logger LOG = Logger.getLogger("test");

    private static ConfigurationNode load(String resource) throws IOException {
        try (InputStream in = TaxesConfigTest.class.getResourceAsStream(resource)) {
            Assertions.assertNotNull(in, resource + " is missing from the plugin resources");
            return YamlConfigurationLoader.builder()
                    .source(() -> new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
                    .build()
                    .load();
        }
    }

    private static TaxSettings shippedSettings() throws IOException {
        TaxSettings settings = load("/taxes.yml").get(TaxSettings.class);
        Assertions.assertNotNull(settings, "taxes.yml did not deserialize into TaxSettings");
        return settings;
    }

    /** N plots carrying no tags — enough to exercise a catch-all rule. */
    private static List<Set<String>> plots(int n) {
        List<Set<String>> plots = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            plots.add(Set.of());
        }
        return plots;
    }

    @Test
    @DisplayName("the shipped taxes.yml deserializes with collection enabled")
    void shippedConfigLoads() throws IOException {
        TaxSettings settings = shippedSettings();
        Assertions.assertTrue(settings.enabled(), "property tax collection should ship enabled");
        Assertions.assertEquals("DCGovernment", settings.governmentAccount());
        Assertions.assertTrue(settings.exemptUuids().isEmpty());
        Assertions.assertFalse(settings.rules().isEmpty(),
                "an empty rules list means no property tax is charged at all");
    }

    @Test
    @DisplayName("every shipped rule's formula compiles")
    void shippedFormulasCompile() throws IOException {
        List<TaxRule> rules = shippedSettings().rules();
        for (int i = 0; i < rules.size(); i++) {
            int index = i;
            Assertions.assertDoesNotThrow(() -> TaxFormula.compile(rules.get(index).formula()),
                    "rule #" + i + " would be dropped at runtime, leaving its plots untaxed");
        }
    }

    @Test
    @DisplayName("the shipped rule charges the documented amounts")
    void shippedRuleMatchesItsDocumentedFigures() throws IOException {
        PropertyTaxPolicy policy = PropertyTaxPolicy.compile(shippedSettings(), LOG);
        // The figures quoted in the resource's own comments.
        Assertions.assertEquals(BigDecimal.ZERO, policy.taxForOwner(plots(2)));
        Assertions.assertEquals(new BigDecimal("5.61"), policy.taxForOwner(plots(3)));
        Assertions.assertEquals(new BigDecimal("35.17"), policy.taxForOwner(plots(5)));
        Assertions.assertEquals(new BigDecimal("228.93"), policy.taxForOwner(plots(10)));
        Assertions.assertEquals(new BigDecimal("1247.91"), policy.taxForOwner(plots(20)));
    }

    @Test
    @DisplayName("the shipped exemption is read from exempt-threshold, not defaulted to 0")
    void exemptThresholdKeyIsRead() throws IOException {
        List<TaxRule> rules = shippedSettings().rules();
        Assertions.assertEquals(2, rules.get(0).exemptThreshold(),
                "a renamed key would silently default the exemption to 0 and tax everyone");
    }

    @Test
    @DisplayName("the shipped rule is a catch-all, so it applies server-wide")
    void shippedRuleIsCatchAll() throws IOException {
        TagMatch match = shippedSettings().rules().get(0).match();
        Assertions.assertTrue(match.all().isEmpty() && match.any().isEmpty());
        Assertions.assertTrue(match.matches(Set.of()), "an untagged plot should still be taxed");
        Assertions.assertTrue(match.matches(Set.of("residential")));
    }

    @Test
    @DisplayName("every tag a shipped rule names is defined in region-tags.yml")
    void shippedRulesOnlyReferenceKnownTags() throws IOException {
        Set<String> known = new HashSet<>();
        for (ConfigurationNode tag : load("/region-tags.yml").node("tags").childrenList()) {
            String id = tag.node("tag-id").getString();
            if (id != null) {
                known.add(id.toLowerCase(Locale.ROOT));
            }
        }
        Assertions.assertFalse(known.isEmpty(), "region-tags.yml defines no tags");

        for (TaxRule rule : shippedSettings().rules()) {
            List<String> referenced = new ArrayList<>(rule.match().all());
            referenced.addAll(rule.match().any());
            for (String tag : referenced) {
                Assertions.assertTrue(known.contains(tag),
                        "taxes.yml matches tag '" + tag + "', which region-tags.yml does not define — "
                                + "the rule would match nothing");
            }
        }
    }

    @Test
    @DisplayName("a config whose rules list is empty charges nothing")
    void emptyRulesChargeNothing() throws ConfigurateException {
        ConfigurationNode node = YamlConfigurationLoader.builder()
                .buildAndLoadString("""
                        enabled: true
                        government-account: "DCGovernment"
                        exempt-uuids: []
                        rules: []
                        """);
        TaxSettings settings = node.get(TaxSettings.class);
        Assertions.assertNotNull(settings);
        Assertions.assertEquals(BigDecimal.ZERO,
                PropertyTaxPolicy.compile(settings, LOG).taxForOwner(plots(100)));
    }

    @Test
    @DisplayName("an emptied rules list is refilled by the startup merge — disable with enabled: false")
    void emptyRulesListIsRefilledByTheStartupMerge() throws IOException {
        // Mirrors Realty#copyDefaultsYaml: the deployed file is merged with the packaged
        // defaults and saved back. Configurate treats an empty list as absent, so
        // `rules: []` does NOT survive a restart — the shipped rule comes back and the
        // server starts charging again. taxes.yml documents `enabled: false` as the
        // off-switch for exactly this reason; this test pins the behaviour that makes
        // that instruction necessary.
        ConfigurationNode deployed = YamlConfigurationLoader.builder()
                .buildAndLoadString("""
                        enabled: true
                        government-account: "TownHall"
                        exempt-uuids: []
                        rules: []
                        """);
        deployed.mergeFrom(load("/taxes.yml"));

        TaxSettings merged = deployed.get(TaxSettings.class);
        Assertions.assertNotNull(merged);
        Assertions.assertEquals("TownHall", merged.governmentAccount(), "the deployed value must win");
        Assertions.assertFalse(merged.rules().isEmpty(),
                "an empty list is indistinguishable from an absent key, so the default returns");
    }

    @Test
    @DisplayName("startup merge fills in the shipped rules when the key is absent entirely")
    void mergeSuppliesRulesWhenKeyIsMissing() throws IOException {
        ConfigurationNode deployed = YamlConfigurationLoader.builder()
                .buildAndLoadString("""
                        enabled: true
                        government-account: "TownHall"
                        """);
        deployed.mergeFrom(load("/taxes.yml"));

        TaxSettings merged = deployed.get(TaxSettings.class);
        Assertions.assertNotNull(merged);
        Assertions.assertFalse(merged.rules().isEmpty(),
                "a config predating `rules` should pick up the shipped default on upgrade");
    }

    @Test
    @DisplayName("a pre-rules config (default-formula / exempt-plot-threshold) no longer taxes")
    void legacyKeysAreInert() throws ConfigurateException {
        // Documents the migration hazard: the old top-level keys are simply ignored,
        // so an un-migrated file collects nothing rather than collecting the old tax.
        ConfigurationNode node = YamlConfigurationLoader.builder()
                .buildAndLoadString("""
                        enabled: true
                        government-account: "DCGovernment"
                        exempt-uuids: []
                        exempt-plot-threshold: 7
                        rules: []
                        default-formula: "0.3 * <plots>^2 + 2.5 * <plots> - 25"
                        """);
        TaxSettings settings = node.get(TaxSettings.class);
        Assertions.assertNotNull(settings, "an un-migrated config must still load, not crash the plugin");
        Assertions.assertTrue(settings.rules().isEmpty());
        Assertions.assertEquals(BigDecimal.ZERO,
                PropertyTaxPolicy.compile(settings, LOG).taxForOwner(plots(100)));
    }
}
