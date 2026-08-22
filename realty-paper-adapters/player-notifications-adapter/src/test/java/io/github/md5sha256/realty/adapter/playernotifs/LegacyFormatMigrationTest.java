package io.github.md5sha256.realty.adapter.playernotifs;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * Covers the replacement of a pre-1.4.2 {@code categories.yml}.
 *
 * <p>An operator upgrading has one on disk, and it would otherwise fail the parse and take the whole
 * module down on start.</p>
 */
class LegacyFormatMigrationTest {

    private static final Logger LOGGER = Logger.getLogger(LegacyFormatMigrationTest.class.getName());

    private static final String LEGACY = """
            categories:
              notification.agent-invited: realty.agent
              notification.outbid: realty.auction
            titles:
              realty.agent: "Realty — Agents"
            expiry-days: 30
            """;

    private static Path writeLegacy(Path dataFolder) throws IOException {
        Files.createDirectories(dataFolder);
        Path file = dataFolder.resolve(CategoriesConfig.CATEGORIES_FILE);
        Files.writeString(file, LEGACY, StandardCharsets.UTF_8);
        return file;
    }

    @Test
    void aLegacyFileIsDetected() {
        YamlConfiguration legacy = CategoriesConfig.load(new StringReader(LEGACY));

        Assertions.assertTrue(CategoriesConfig.isLegacyFormat(legacy));
    }

    @Test
    void aCurrentFileIsNotMistakenForALegacyOne(@TempDir Path dataFolder) throws IOException {
        CategoriesConfig.read(dataFolder, LOGGER);

        YamlConfiguration current = CategoriesConfig.load(
                Files.newBufferedReader(dataFolder.resolve(CategoriesConfig.CATEGORIES_FILE)));

        Assertions.assertFalse(CategoriesConfig.isLegacyFormat(current));
    }

    /** A missing categories section is a different failure, and must not trigger a rewrite. */
    @Test
    void aFileWithNoCategoriesSectionIsNotTreatedAsLegacy() {
        YamlConfiguration empty = CategoriesConfig.load(new StringReader("expiry-days: 30\n"));

        Assertions.assertFalse(CategoriesConfig.isLegacyFormat(empty));
    }

    @Test
    void aLegacyFileIsReplacedAndTheModuleStarts(@TempDir Path dataFolder) throws IOException {
        writeLegacy(dataFolder);

        NotificationCategoryMapper mapper =
                CategoriesConfig.readMapper(CategoriesConfig.read(dataFolder, LOGGER));

        Assertions.assertTrue(mapper.dataTypes().contains("realty.auction"));
        Assertions.assertEquals("realty.auction", mapper.dataTypeFor("notification.outbid"));
    }

    @Test
    void theLegacyFileIsKeptAsABackup(@TempDir Path dataFolder) throws IOException {
        writeLegacy(dataFolder);

        CategoriesConfig.read(dataFolder, LOGGER);

        Path backup = dataFolder.resolve(
                CategoriesConfig.CATEGORIES_FILE + CategoriesConfig.LEGACY_BACKUP_SUFFIX);
        Assertions.assertTrue(Files.isRegularFile(backup));
        Assertions.assertEquals(LEGACY, Files.readString(backup, StandardCharsets.UTF_8));
    }

    /**
     * The replacement runs once. A second start sees a current file and leaves it alone, so an
     * operator who re-edits it after the upgrade does not have their work replaced again.
     */
    @Test
    void aSecondStartDoesNotReplaceTheReplacement(@TempDir Path dataFolder) throws IOException {
        writeLegacy(dataFolder);
        CategoriesConfig.read(dataFolder, LOGGER);
        Path live = dataFolder.resolve(CategoriesConfig.CATEGORIES_FILE);
        String edited = Files.readString(live, StandardCharsets.UTF_8)
                .replace("Realty auctions", "Auction stuff");
        Files.writeString(live, edited, StandardCharsets.UTF_8);

        CategoriesConfig.read(dataFolder, LOGGER);

        Assertions.assertEquals(edited, Files.readString(live, StandardCharsets.UTF_8));
    }
}
