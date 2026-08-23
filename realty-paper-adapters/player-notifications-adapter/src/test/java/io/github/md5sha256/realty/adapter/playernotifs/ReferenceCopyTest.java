package io.github.md5sha256.realty.adapter.playernotifs;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Covers the reference copy every config file must ship: a regenerated {@code defaults/} copy an
 * operator can diff their own file against after an upgrade.
 */
class ReferenceCopyTest {

    private static Path reference(Path dataFolder) {
        return dataFolder.resolve(AdapterConfig.DEFAULTS_DIR).resolve(AdapterConfig.REFERENCE_FILE);
    }

    @Test
    void aFirstStartWritesBothTheLiveFileAndTheReferenceCopy(@TempDir Path dataFolder) {
        AdapterConfig.read(dataFolder);

        Assertions.assertTrue(Files.isRegularFile(dataFolder.resolve(AdapterConfig.CONFIG_FILE)));
        Assertions.assertTrue(Files.isRegularFile(reference(dataFolder)));
    }

    /**
     * The operator's own file is theirs — first start seeds it and nothing overwrites it afterwards.
     */
    @Test
    void aLaterStartLeavesTheOperatorsFileAlone(@TempDir Path dataFolder) throws IOException {
        AdapterConfig.read(dataFolder);
        Path live = dataFolder.resolve(AdapterConfig.CONFIG_FILE);
        String edited = Files.readString(live, StandardCharsets.UTF_8)
                .replace("expiry-days: 30", "expiry-days: 7");
        Files.writeString(live, edited, StandardCharsets.UTF_8);

        AdapterConfig config = AdapterConfig.read(dataFolder);

        Assertions.assertEquals(edited, Files.readString(live, StandardCharsets.UTF_8));
        Assertions.assertEquals(Duration.ofDays(7), config.expiry());
    }

    /**
     * The reference copy is the opposite: stale is worse than absent, since its only job is to answer
     * "what does a current file look like?".
     */
    @Test
    void aStaleReferenceCopyIsOverwrittenOnEveryStart(@TempDir Path dataFolder) throws IOException {
        AdapterConfig.read(dataFolder);
        Files.writeString(reference(dataFolder), "# left over from an older version\n",
                StandardCharsets.UTF_8);

        AdapterConfig.read(dataFolder);

        String refreshed = Files.readString(reference(dataFolder), StandardCharsets.UTF_8);
        Assertions.assertFalse(refreshed.contains("left over"));
        Assertions.assertTrue(refreshed.contains("expiry-days"));
    }

    /** The shipped reference must itself be loadable, or it documents a file that would not start. */
    @Test
    void theReferenceCopyParses(@TempDir Path dataFolder) throws IOException {
        AdapterConfig.read(dataFolder);

        try (Reader reader = Files.newBufferedReader(reference(dataFolder))) {
            AdapterConfig config = AdapterConfig.from(YamlConfiguration.loadConfiguration(reader));
            Assertions.assertEquals(Duration.ofDays(30), config.expiry());
        }
    }

    @Test
    void theReferenceCopyIsWrittenEvenWhenTheOperatorAlreadyHasAFile(@TempDir Path dataFolder)
            throws IOException {
        Files.createDirectories(dataFolder);
        Files.writeString(dataFolder.resolve(AdapterConfig.CONFIG_FILE), "expiry-days: 1\n",
                StandardCharsets.UTF_8);

        AdapterConfig config = AdapterConfig.read(dataFolder);

        Assertions.assertTrue(Files.isRegularFile(reference(dataFolder)));
        Assertions.assertEquals(Duration.ofDays(1), config.expiry());
    }

    /** A file predating {@code expiry-days} still starts, on the compiled-in default. */
    @Test
    void aMissingExpiryFallsBackToTheDefault(@TempDir Path dataFolder) throws IOException {
        Files.createDirectories(dataFolder);
        Files.writeString(dataFolder.resolve(AdapterConfig.CONFIG_FILE), "# nothing set\n",
                StandardCharsets.UTF_8);

        Assertions.assertEquals(Duration.ofDays(30), AdapterConfig.read(dataFolder).expiry());
    }

    private static Path titlesReference(Path dataFolder) {
        return dataFolder.resolve(AdapterConfig.DEFAULTS_DIR)
                .resolve(TitleConfig.REFERENCE_FILE);
    }

    @Test
    void aFirstStartWritesBothTheLiveTitlesFileAndItsReferenceCopy(@TempDir Path dataFolder) {
        TitleConfig.read(dataFolder);

        Assertions.assertTrue(Files.isRegularFile(dataFolder.resolve(TitleConfig.TITLES_FILE)));
        Assertions.assertTrue(Files.isRegularFile(titlesReference(dataFolder)));
    }

    /** The operator's titles are theirs — seeded once, never rewritten. */
    @Test
    void aLaterStartLeavesTheOperatorsTitlesAlone(@TempDir Path dataFolder) throws IOException {
        TitleConfig.read(dataFolder);
        Path live = dataFolder.resolve(TitleConfig.TITLES_FILE);
        String edited = Files.readString(live, StandardCharsets.UTF_8)
                .replace("Lease expired", "Your lease ran out");
        Files.writeString(live, edited, StandardCharsets.UTF_8);

        TitleConfig titles = TitleConfig.read(dataFolder);

        Assertions.assertEquals(edited, Files.readString(live, StandardCharsets.UTF_8));
        Assertions.assertEquals("Your lease ran out", PlainTextComponentSerializer.plainText()
                .serialize(titles.titleFor("notification.leasehold-expired")));
    }

    @Test
    void aStaleTitlesReferenceCopyIsOverwrittenOnEveryStart(@TempDir Path dataFolder)
            throws IOException {
        TitleConfig.read(dataFolder);
        Files.writeString(titlesReference(dataFolder), "# left over from an older version\n",
                StandardCharsets.UTF_8);

        TitleConfig.read(dataFolder);

        String refreshed = Files.readString(titlesReference(dataFolder), StandardCharsets.UTF_8);
        Assertions.assertFalse(refreshed.contains("left over"));
        Assertions.assertTrue(refreshed.contains("notification.leasehold-expired"));
    }

    /**
     * The shipped reference must itself load, and must load to the titles it documents — a reference
     * copy whose keys were nested by the path separator would describe a file that overrides nothing.
     */
    @Test
    void theTitlesReferenceCopyParsesToTheCompiledTitles(@TempDir Path dataFolder)
            throws IOException {
        TitleConfig.read(dataFolder);

        try (Reader reader = Files.newBufferedReader(titlesReference(dataFolder))) {
            TitleConfig titles = TitleConfig.load(reader);
            Assertions.assertEquals("Lease expired", PlainTextComponentSerializer.plainText()
                    .serialize(titles.titleFor("notification.leasehold-expired")));
        }
    }
}
