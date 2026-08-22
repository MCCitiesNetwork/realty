package io.github.md5sha256.realty.adapter.essentials;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

class EssentialsAdapterConfigTest {

    private static EssentialsAdapterConfig parse(String yaml) {
        return EssentialsAdapterConfig.from(
                YamlConfiguration.loadConfiguration(new StringReader(yaml)));
    }

    private static Path reference(Path dataFolder) {
        return dataFolder.resolve(EssentialsAdapterConfig.DEFAULTS_DIR)
                .resolve(EssentialsAdapterConfig.REFERENCE_FILE);
    }

    @Test
    void notificationsCanBeTurnedOff() {
        Assertions.assertFalse(parse("notifications-enabled: false\n").notificationsEnabled());
    }

    @Test
    void notificationsCanBeTurnedOn() {
        Assertions.assertTrue(parse("notifications-enabled: true\n").notificationsEnabled());
    }

    /**
     * An operator whose file predates this setting keeps the behaviour they already had, rather than
     * silently losing mail delivery on upgrade.
     */
    @Test
    void anAbsentSettingDefaultsToEnabled() {
        Assertions.assertTrue(parse("# nothing here\n").notificationsEnabled());
    }

    @Test
    void aFirstStartWritesBothTheLiveFileAndTheReferenceCopy(@TempDir Path dataFolder) {
        EssentialsAdapterConfig config = EssentialsAdapterConfig.read(dataFolder);

        Assertions.assertTrue(
                Files.isRegularFile(dataFolder.resolve(EssentialsAdapterConfig.CONFIG_FILE)));
        Assertions.assertTrue(Files.isRegularFile(reference(dataFolder)));
        Assertions.assertTrue(config.notificationsEnabled(), "the shipped default is enabled");
    }

    @Test
    void aLaterStartLeavesTheOperatorsFileAlone(@TempDir Path dataFolder) throws IOException {
        EssentialsAdapterConfig.read(dataFolder);
        Path live = dataFolder.resolve(EssentialsAdapterConfig.CONFIG_FILE);
        Files.writeString(live, "notifications-enabled: false\n", StandardCharsets.UTF_8);

        EssentialsAdapterConfig config = EssentialsAdapterConfig.read(dataFolder);

        Assertions.assertFalse(config.notificationsEnabled());
        Assertions.assertEquals("notifications-enabled: false\n",
                Files.readString(live, StandardCharsets.UTF_8));
    }

    @Test
    void aStaleReferenceCopyIsOverwrittenOnEveryStart(@TempDir Path dataFolder) throws IOException {
        EssentialsAdapterConfig.read(dataFolder);
        Files.writeString(reference(dataFolder), "# left over\n", StandardCharsets.UTF_8);

        EssentialsAdapterConfig.read(dataFolder);

        String refreshed = Files.readString(reference(dataFolder), StandardCharsets.UTF_8);
        Assertions.assertFalse(refreshed.contains("left over"));
        Assertions.assertTrue(refreshed.contains("notifications-enabled"));
    }

    /** A reference copy that would not load documents a lie. */
    @Test
    void theReferenceCopyParses(@TempDir Path dataFolder) throws IOException {
        EssentialsAdapterConfig.read(dataFolder);

        try (Reader reader = Files.newBufferedReader(reference(dataFolder))) {
            Assertions.assertTrue(
                    EssentialsAdapterConfig.from(YamlConfiguration.loadConfiguration(reader))
                            .notificationsEnabled());
        }
    }
}
