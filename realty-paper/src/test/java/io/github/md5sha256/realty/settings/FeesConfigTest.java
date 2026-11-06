package io.github.md5sha256.realty.settings;

import io.github.md5sha256.realty.api.FeeSide;
import io.github.md5sha256.realty.api.LevyCatalog;
import io.github.md5sha256.realty.api.LevyDefinition;
import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.api.PartyKind;
import io.github.md5sha256.realty.command.util.PartyResolver;
import io.github.md5sha256.realty.fees.ConfiguredLevyCatalog;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/** Guards the shipped fees.yml and the compilation of levies into a catalog. */
class FeesConfigTest {

    private static final Logger LOG = Logger.getLogger("test");
    private static final UUID KNOWN = new UUID(0, 1);

    private static final String TOWNVILLE = """
            levies:
              - name: townville-levy
                enabled: true
                paid-by: buyer
                recipient: authority
                rules:
                  - match:
                      any: [residential]
                    tiers:
                      - { from-plots: 0, percent: 2 }
                      - { from-plots: 5, percent: 8 }
            """;

    private static FeeSettings parse(String yaml) throws IOException {
        FeeSettings settings = YamlConfigurationLoader.builder().buildAndLoadString(yaml).get(FeeSettings.class);
        Assertions.assertNotNull(settings);
        return settings;
    }

    private static FeeSettings shipped() throws IOException {
        try (InputStream in = FeesConfigTest.class.getResourceAsStream("/fees.yml")) {
            Assertions.assertNotNull(in, "fees.yml is missing from the plugin resources");
            FeeSettings settings = YamlConfigurationLoader.builder()
                    .source(() -> new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
                    .build().load().get(FeeSettings.class);
            Assertions.assertNotNull(settings);
            return settings;
        }
    }

    private static PartyResolver resolver() {
        PartyResolver resolver = Mockito.mock(PartyResolver.class);
        Mockito.when(resolver.resolve(Mockito.eq("Known"), Mockito.isNull(), Mockito.isNull()))
                .thenReturn(new PartyResolver.Resolution.Resolved(Party.personal(KNOWN)));
        Mockito.when(resolver.resolve(Mockito.eq("Nobody"), Mockito.isNull(), Mockito.isNull()))
                .thenReturn(new PartyResolver.Resolution.Refused("unknown", "Nobody"));
        return resolver;
    }

    @Test
    @DisplayName("the shipped file has one disabled default national levy")
    void shippedFile_hasDisabledDefaultNationalLevy() throws IOException {
        LevyCatalog catalog = ConfiguredLevyCatalog.compile(shipped(), resolver(), LOG);
        Assertions.assertEquals(1, catalog.all().size());
        LevyDefinition levy = catalog.find("national-levy").orElseThrow();
        Assertions.assertFalse(levy.enabled());
        Assertions.assertTrue(levy.isDefault());
        Assertions.assertSame(levy, catalog.defaultLevy().orElseThrow());
        Assertions.assertEquals(PartyKind.GOVERNMENT, levy.authorityKind());
        Assertions.assertNull(levy.recipient());
    }

    @Test
    @DisplayName("tiers and paid-by are read")
    void parsesTiersAndPaidBy() throws IOException {
        LevyCatalog catalog = ConfiguredLevyCatalog.compile(parse(TOWNVILLE), resolver(), LOG);
        LevyDefinition levy = catalog.find("townville-levy").orElseThrow();
        Assertions.assertEquals(FeeSide.BUYER, levy.paidBy());
        Assertions.assertNull(levy.recipient());
        Assertions.assertNull(levy.authorityKind());
        Assertions.assertEquals(2, levy.rules().get(0).tiers().size());
        Assertions.assertEquals(5, levy.rules().get(0).tiers().get(1).fromPlots());
        Assertions.assertEquals(8.0, levy.percentFor(Set.of("residential"), 5).orElseThrow());
        Assertions.assertEquals(2.0, levy.percentFor(Set.of("residential"), 4).orElseThrow());
        Assertions.assertTrue(levy.percentFor(Set.of("commercial"), 5).isEmpty());
    }

    @Test
    @DisplayName("two default levies give an empty catalog")
    void twoDefaults_givesEmptyCatalog() throws IOException {
        FeeSettings settings = parse("""
                levies:
                  - { name: a, enabled: true, default: true, paid-by: buyer }
                  - { name: b, enabled: true, default: true, paid-by: seller }
                """);
        Assertions.assertSame(LevyCatalog.EMPTY, ConfiguredLevyCatalog.compile(settings, resolver(), LOG));
    }

    @Test
    @DisplayName("a levy with an out-of-range tier is dropped")
    void invalidTier_dropsThatLevy() throws IOException {
        FeeSettings settings = parse("""
                levies:
                  - { name: neg, enabled: true, paid-by: buyer, rules: [{ tiers: [{ from-plots: 0, percent: -1 }] }] }
                  - { name: big, enabled: true, paid-by: buyer, rules: [{ tiers: [{ from-plots: 0, percent: 101 }] }] }
                  - { name: nan, enabled: true, paid-by: buyer, rules: [{ tiers: [{ from-plots: 0, percent: .nan }] }] }
                  - { name: from, enabled: true, paid-by: buyer, rules: [{ tiers: [{ from-plots: -1, percent: 5 }] }] }
                  - { name: ok, enabled: true, paid-by: buyer, rules: [{ tiers: [{ from-plots: 0, percent: 100 }] }] }
                """);
        LevyCatalog catalog = ConfiguredLevyCatalog.compile(settings, resolver(), LOG);
        Assertions.assertEquals(1, catalog.all().size());
        Assertions.assertTrue(catalog.find("ok").isPresent());
    }

    @Test
    @DisplayName("a recipient that names nothing usable drops the levy instead of paying the authority")
    void malformedRecipient_dropsThatLevy() throws IOException {
        FeeSettings settings = parse("""
                levies:
                  - { name: typo, enabled: true, paid-by: buyer, recipient: { nmae: Known } }
                  - { name: empty, enabled: true, paid-by: buyer, recipient: {} }
                  - { name: auth, enabled: true, paid-by: buyer, recipient: authority }
                  - { name: omitted, enabled: true, paid-by: buyer }
                """);
        LevyCatalog catalog = ConfiguredLevyCatalog.compile(settings, resolver(), LOG);
        Assertions.assertTrue(catalog.find("typo").isEmpty());
        Assertions.assertTrue(catalog.find("empty").isEmpty());
        Assertions.assertTrue(catalog.find("auth").isPresent());
        Assertions.assertTrue(catalog.find("omitted").isPresent());
    }

    @Test
    @DisplayName("a levy whose recipient does not resolve is dropped")
    void unknownRecipient_dropsThatLevy() throws IOException {
        FeeSettings settings = parse("""
                levies:
                  - name: bad
                    enabled: true
                    paid-by: buyer
                    recipient: { name: Nobody }
                  - name: good
                    enabled: true
                    paid-by: seller
                    recipient: { name: Known }
                """);
        LevyCatalog catalog = ConfiguredLevyCatalog.compile(settings, resolver(), LOG);
        Assertions.assertTrue(catalog.find("bad").isEmpty());
        Assertions.assertEquals(Party.personal(KNOWN), catalog.find("good").orElseThrow().recipient());
    }

    @Test
    void missingChoices_namesEachChosenLevyTheCatalogLacks_once() throws IOException {
        LevyCatalog catalog = ConfiguredLevyCatalog.compile(parse(TOWNVILLE), resolver(), LOG);
        String defined = catalog.all().getFirst().name();

        Assertions.assertEquals(java.util.List.of("old-levy", "gone"), ConfiguredLevyCatalog.missingChoices(catalog,
                java.util.List.of(defined.toUpperCase(java.util.Locale.ROOT), "old-levy", "gone", "old-levy")));
        Assertions.assertEquals(java.util.List.of(), ConfiguredLevyCatalog.missingChoices(catalog,
                java.util.List.of(defined)));
    }
}
