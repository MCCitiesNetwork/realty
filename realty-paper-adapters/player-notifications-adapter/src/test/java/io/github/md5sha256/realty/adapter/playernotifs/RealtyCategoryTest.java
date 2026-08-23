package io.github.md5sha256.realty.adapter.playernotifs;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Covers the compiled category table: routing, its defaults, and — the part that used to be a runtime
 * check on an operator's file — that it actually covers Realty's message keys.
 */
class RealtyCategoryTest {

    /**
     * Realty's own {@code messages.yml}, reached relatively because the category table is this
     * module's claim about <em>that</em> file's keys and nothing else can check it.
     */
    private static final Path MESSAGES =
            Path.of("..", "..", "realty-paper", "src", "main", "resources", "messages.yml");

    @Test
    void aClaimedKeyRoutesToItsCategory() {
        Assertions.assertEquals(RealtyCategory.AUCTION,
                RealtyCategory.forMessageKey("notification.outbid"));
        Assertions.assertEquals(RealtyCategory.LEASE,
                RealtyCategory.forMessageKey("notification.region-rented"));
        Assertions.assertEquals(RealtyCategory.AGENT,
                RealtyCategory.forMessageKey("notification.agent-invited"));
    }

    /**
     * A key no category claims is routed, never dropped: Realty gains message keys faster than this
     * table does, and a notification it has never seen is still one a player should receive.
     */
    @Test
    void anUnclaimedKeyFallsBackWithoutBeingDropped() {
        Assertions.assertEquals(RealtyCategory.GENERAL,
                RealtyCategory.forMessageKey("notification.some-future-key"));
        Assertions.assertFalse(RealtyCategory.isClaimed("notification.some-future-key"));
        Assertions.assertTrue(RealtyCategory.isClaimed("notification.region-bought"));
    }

    @Test
    void everyCategoryHasADataTypeLabelAndDescription() {
        Set<String> dataTypes = new HashSet<>();
        for (RealtyCategory category : RealtyCategory.values()) {
            Assertions.assertTrue(category.dataType().startsWith("realty."), category.dataType());
            Assertions.assertFalse(category.label().isBlank(), category.name());
            Assertions.assertFalse(category.description().isBlank(), category.name());
            Assertions.assertTrue(dataTypes.add(category.dataType()), category.dataType());
        }
    }

    /**
     * Every claimed key carries its own title, and no category reuses one across two of its keys.
     *
     * <p>A blank or duplicated title puts the row back where this table started: PN's inbox lists rows
     * by title alone, so two keys in one category sharing a title are two rows a player cannot tell
     * apart. Across categories a repeat is harmless — those rows differ by category anyway.</p>
     */
    @Test
    void everyClaimedKeyHasItsOwnTitleWithinItsCategory() {
        for (RealtyCategory category : RealtyCategory.values()) {
            Set<String> titles = new HashSet<>();
            for (Map.Entry<String, String> entry : category.titlesByMessageKey().entrySet()) {
                Assertions.assertFalse(entry.getValue().isBlank(), entry.getKey());
                Assertions.assertTrue(titles.add(entry.getValue()),
                        category.name() + " reuses the title '" + entry.getValue() + "'");
            }
        }
    }

    /** A claimed key renders its own summary; an unclaimed one falls back to the category label. */
    @Test
    void titleForFallsBackToTheCategoryLabel() {
        Assertions.assertEquals("Lease expired",
                RealtyCategory.titleFor("notification.leasehold-expired"));
        Assertions.assertEquals(RealtyCategory.GENERAL.label(),
                RealtyCategory.titleFor("notification.some-future-key"));
    }

    /**
     * The replacement for the duplicate-key check the config parser used to make. Loading the enum at
     * all builds the index, so a key claimed twice fails every test in this class rather than
     * silently making the winning category depend on declaration order.
     */
    @Test
    void noMessageKeyIsClaimedTwice() {
        List<String> claimed = new ArrayList<>();
        for (RealtyCategory category : RealtyCategory.values()) {
            claimed.addAll(category.messageKeys());
        }
        Assertions.assertEquals(claimed.size(), Set.copyOf(claimed).size(),
                "the same message key appears under two categories");
    }

    /**
     * Every {@code notification.*} key Realty can fire is claimed by a category.
     *
     * <p>Uncovered keys are not broken — they fall back to {@link RealtyCategory#GENERAL} — but they
     * are almost always an oversight, and a player who disables "Realty" then stops receiving them.
     * Failing here is how a new notification key gets a deliberate home rather than a default one.</p>
     */
    @Test
    void everyRealtyNotificationKeyIsClaimed() throws IOException {
        Assertions.assertTrue(Files.isRegularFile(MESSAGES),
                "expected Realty's messages.yml at " + MESSAGES.toAbsolutePath());

        YamlConfiguration messages;
        try (Reader reader = Files.newBufferedReader(MESSAGES)) {
            messages = YamlConfiguration.loadConfiguration(reader);
        }
        ConfigurationSection section = messages.getConfigurationSection("notification");
        Assertions.assertNotNull(section, "messages.yml has no 'notification' section");

        Set<String> unclaimed = new TreeSet<>();
        for (String key : section.getKeys(false)) {
            String messageKey = "notification." + key;
            if (!RealtyCategory.isClaimed(messageKey)) {
                unclaimed.add(messageKey);
            }
        }
        Assertions.assertEquals(Set.of(), unclaimed,
                "these message keys belong to no RealtyCategory and fall back to "
                        + RealtyCategory.FALLBACK.dataType());
    }

    /**
     * The mirror of the above: the table must not claim keys Realty cannot fire, which would otherwise
     * hide a rename behind a category that quietly claims nothing.
     */
    @Test
    void noCategoryClaimsAKeyRealtyDoesNotHave() throws IOException {
        YamlConfiguration messages;
        try (Reader reader = Files.newBufferedReader(MESSAGES)) {
            messages = YamlConfiguration.loadConfiguration(reader);
        }
        ConfigurationSection section = messages.getConfigurationSection("notification");
        Assertions.assertNotNull(section);

        Set<String> known = new HashSet<>();
        for (String key : section.getKeys(false)) {
            known.add("notification." + key);
        }

        Set<String> stale = new TreeSet<>();
        for (RealtyCategory category : RealtyCategory.values()) {
            for (String messageKey : category.messageKeys()) {
                if (!known.contains(messageKey)) {
                    stale.add(messageKey);
                }
            }
        }
        Assertions.assertEquals(Set.of(), stale, "these claimed keys are not in messages.yml");
    }
}
