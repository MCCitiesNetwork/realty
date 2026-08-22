package io.github.md5sha256.realty.adapter.playernotifs;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Covers the reference copy every config file must ship: a regenerated {@code defaults/} copy an
 * operator can diff their own file against after an upgrade.
 */
class ReferenceCopyTest {

    private static Path reference(Path dataFolder) {
        return dataFolder.resolve(CategoriesConfig.DEFAULTS_DIR).resolve(CategoriesConfig.REFERENCE_FILE);
    }

    @Test
    void aFirstStartWritesBothTheLiveFileAndTheReferenceCopy(@TempDir Path dataFolder) {
        CategoriesConfig.read(dataFolder);

        Assertions.assertTrue(Files.isRegularFile(dataFolder.resolve(CategoriesConfig.CATEGORIES_FILE)));
        Assertions.assertTrue(Files.isRegularFile(reference(dataFolder)));
    }

    /**
     * The operator's own file is theirs — first start seeds it and nothing overwrites it afterwards.
     */
    @Test
    void aLaterStartLeavesTheOperatorsFileAlone(@TempDir Path dataFolder) throws IOException {
        CategoriesConfig.read(dataFolder);
        Path live = dataFolder.resolve(CategoriesConfig.CATEGORIES_FILE);
        String edited = Files.readString(live, StandardCharsets.UTF_8)
                .replace("Realty auctions", "Auction stuff");
        Files.writeString(live, edited, StandardCharsets.UTF_8);

        CategoriesConfig.read(dataFolder);

        Assertions.assertEquals(edited, Files.readString(live, StandardCharsets.UTF_8));
    }

    /**
     * The reference copy is the opposite: stale is worse than absent, since its only job is to answer
     * "what does a current file look like?".
     */
    @Test
    void aStaleReferenceCopyIsOverwrittenOnEveryStart(@TempDir Path dataFolder) throws IOException {
        CategoriesConfig.read(dataFolder);
        Files.writeString(reference(dataFolder), "# left over from an older version\n",
                StandardCharsets.UTF_8);

        CategoriesConfig.read(dataFolder);

        String refreshed = Files.readString(reference(dataFolder), StandardCharsets.UTF_8);
        Assertions.assertFalse(refreshed.contains("left over"));
        Assertions.assertTrue(refreshed.contains("fallback-category"));
    }

    /** The shipped reference must itself be loadable, or it documents a file that would not start. */
    @Test
    void theReferenceCopyParses(@TempDir Path dataFolder) throws IOException {
        CategoriesConfig.read(dataFolder);

        try (Reader reader = Files.newBufferedReader(reference(dataFolder))) {
            NotificationCategoryMapper mapper = CategoriesConfig.readMapper(CategoriesConfig.load(reader));
            Assertions.assertFalse(mapper.dataTypes().isEmpty());
        }
    }

    @Test
    void theReferenceCopyIsWrittenEvenWhenTheOperatorAlreadyHasAFile(@TempDir Path dataFolder)
            throws IOException {
        Files.createDirectories(dataFolder);
        Files.writeString(dataFolder.resolve(CategoriesConfig.CATEGORIES_FILE), """
                categories:
                  realty.general:
                    label: "Realty"
                """, StandardCharsets.UTF_8);

        CategoriesConfig.read(dataFolder);

        Assertions.assertTrue(Files.isRegularFile(reference(dataFolder)));
    }
}
