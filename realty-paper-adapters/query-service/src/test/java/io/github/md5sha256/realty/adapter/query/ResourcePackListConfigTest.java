package io.github.md5sha256.realty.adapter.query;

import io.github.md5sha256.realty.adapter.query.json.ResourcePackEntry;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;

/**
 * The {@code resource-packs} list: several packs, highest priority first, each carrying
 * the credits its own licence asks for.
 */
class ResourcePackListConfigTest {

    private static QueryServiceConfig parse(String yaml) {
        return QueryServiceConfig.from(YamlConfiguration.loadConfiguration(new StringReader(yaml)));
    }

    @Test
    void readsPacksInTheOrderTheyAreWritten() {
        List<ResourcePackEntry> packs = parse("""
                resource-packs:
                  - url: "https://cdn.example.com/override.zip"
                  - url: "https://cdn.example.com/base.zip"
                """).resourcePacks();
        Assertions.assertEquals(2, packs.size());
        Assertions.assertEquals("https://cdn.example.com/override.zip", packs.get(0).url());
        Assertions.assertEquals("https://cdn.example.com/base.zip", packs.get(1).url());
    }

    @Test
    void acceptsABareStringAsAPackWithNoCredits() {
        List<ResourcePackEntry> packs =
                parse("resource-packs:\n  - \"https://cdn.example.com/base.zip\"\n").resourcePacks();
        Assertions.assertEquals(1, packs.size());
        Assertions.assertEquals("https://cdn.example.com/base.zip", packs.get(0).url());
        Assertions.assertTrue(packs.get(0).attribution().isEmpty());
    }

    @Test
    void keepsEachPacksCreditsWithThatPack() {
        // Credits are per pack, not per server: two packs may be licensed differently, and
        // a credit attached to the wrong one credits the wrong author.
        List<ResourcePackEntry> packs = parse("""
                resource-packs:
                  - url: "https://cdn.example.com/override.zip"
                    attribution:
                      - text: "Example Pack 32x"
                        url: "https://packs.example.com/"
                  - url: "https://cdn.example.com/base.zip"
                    attribution:
                      - "CC BY 4.0"
                """).resourcePacks();
        Assertions.assertEquals(1, packs.get(0).attribution().size());
        Assertions.assertEquals("Example Pack 32x", packs.get(0).attribution().get(0).text());
        Assertions.assertEquals("https://packs.example.com/", packs.get(0).attribution().get(0).url());
        Assertions.assertEquals(1, packs.get(1).attribution().size());
        Assertions.assertEquals("CC BY 4.0", packs.get(1).attribution().get(0).text());
        Assertions.assertNull(packs.get(1).attribution().get(0).url());
    }

    @Test
    void rejectsAPackUrlTheBrowserCouldNotFetch() {
        IllegalStateException ex = Assertions.assertThrows(IllegalStateException.class,
                () -> parse("resource-packs:\n  - \"packs/pack.zip\"\n"));
        Assertions.assertTrue(ex.getMessage().contains("resource-packs"), ex.getMessage());
    }

    @Test
    void rejectsAnEntryWithNoUrl() {
        IllegalStateException ex = Assertions.assertThrows(IllegalStateException.class,
                () -> parse("resource-packs:\n  - attribution:\n      - \"CC BY 4.0\"\n"));
        Assertions.assertTrue(ex.getMessage().contains("resource-packs"), ex.getMessage());
    }

    @Test
    void rejectsTheSamePackListedTwice() {
        // Its priority would be ambiguous, and one of the two entries can only be a mistake.
        IllegalStateException ex = Assertions.assertThrows(IllegalStateException.class,
                () -> parse("""
                        resource-packs:
                          - "https://cdn.example.com/pack.zip"
                          - "https://cdn.example.com/pack.zip"
                        """));
        Assertions.assertTrue(ex.getMessage().contains("resource-packs"), ex.getMessage());
    }

    @Test
    void fallsBackToTheSinglePackSettingsWhenTheListIsAbsent() {
        // An operator upgrading has resource-pack-url set and no reason to have touched
        // anything; their pack must keep working untouched.
        List<ResourcePackEntry> packs = parse("""
                resource-pack-url: "https://cdn.example.com/pack.zip"
                resource-pack-attribution:
                  - "CC BY 4.0"
                """).resourcePacks();
        Assertions.assertEquals(1, packs.size());
        Assertions.assertEquals("https://cdn.example.com/pack.zip", packs.get(0).url());
        Assertions.assertEquals("CC BY 4.0", packs.get(0).attribution().get(0).text());
    }

    @Test
    void prefersTheListWhenBothAreSet() {
        List<ResourcePackEntry> packs = parse("""
                resource-pack-url: "https://cdn.example.com/legacy.zip"
                resource-packs:
                  - "https://cdn.example.com/current.zip"
                """).resourcePacks();
        Assertions.assertEquals(1, packs.size());
        Assertions.assertEquals("https://cdn.example.com/current.zip", packs.get(0).url());
    }

    @Test
    void reportsNoPacksWhenNothingIsConfigured() {
        Assertions.assertTrue(parse("# nothing\n").resourcePacks().isEmpty());
        Assertions.assertTrue(parse("resource-packs: []\n").resourcePacks().isEmpty());
    }

    /**
     * The commented example in the shipped {@code config.yml} must parse. A reference copy
     * that would not start documents a lie, and this example is the only place an operator
     * sees the list's shape.
     */
    @Test
    void theExampleInTheShippedConfigParses() throws Exception {
        String bundled;
        try (java.io.InputStream in = QueryServiceConfig.class.getClassLoader()
                .getResourceAsStream(QueryServiceConfig.CONFIG_FILE)) {
            Assertions.assertNotNull(in, "config.yml is not on the classpath");
            bundled = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        StringBuilder example = new StringBuilder();
        boolean inExample = false;
        for (String line : bundled.split("\\R")) {
            if (line.startsWith("#   resource-packs:")) {
                inExample = true;
            } else if (inExample && !line.startsWith("#  ")) {
                break;
            }
            if (inExample) {
                example.append(line.substring(1)).append('\n');
            }
        }
        Assertions.assertTrue(example.length() > 0, "no resource-packs example found in config.yml");

        List<ResourcePackEntry> packs = parse(example.toString()).resourcePacks();
        Assertions.assertEquals(2, packs.size(), example.toString());
        Assertions.assertEquals("https://packs.example.com/server-override.zip", packs.get(0).url());
        Assertions.assertEquals("Textures: Example Pack 32x", packs.get(0).attribution().get(0).text());
        Assertions.assertEquals("https://packs.example.com/vanilla-base.zip", packs.get(1).url());
        Assertions.assertEquals("CC BY 4.0", packs.get(1).attribution().get(0).text());
    }
}
