package io.github.md5sha256.realty.adapter.essentials;

import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/**
 * Reads the module's {@code config.yml}.
 *
 * <p>Kept out of {@link EssentialsAdapterModule} so it can be tested directly: the module extends
 * {@code SimplePluginModule}, and reaching it from a test would drag {@code plugin-infrastructure} —
 * a {@code compileOnly} dependency — onto the test classpath.</p>
 */
public final class EssentialsAdapterConfig {

    static final String CONFIG_FILE = "config.yml";
    /** See the project config rules: every operator config ships a regenerated reference copy. */
    static final String DEFAULTS_DIR = "defaults";
    static final String REFERENCE_FILE = "default-config.yml";

    private static final String NOTIFICATIONS_ENABLED = "notifications-enabled";

    private final boolean notificationsEnabled;

    EssentialsAdapterConfig(boolean notificationsEnabled) {
        this.notificationsEnabled = notificationsEnabled;
    }

    /**
     * Whether Realty notifications are delivered as EssentialsX mail.
     *
     * <p>Only mail delivery is switchable. The teleport-safety integration this module also installs
     * is not affected: it is a correctness fix rather than a delivery channel, and a server running
     * EssentialsX wants EssentialsX's own block checks whatever it does about notifications.</p>
     */
    public boolean notificationsEnabled() {
        return this.notificationsEnabled;
    }

    /**
     * Reads the operator's {@code config.yml}, writing the bundled default there first if they have
     * none, and refreshing the reference copy beside it either way.
     */
    public static @NotNull EssentialsAdapterConfig read(@NotNull Path dataFolder) {
        Objects.requireNonNull(dataFolder, "dataFolder");
        Path file = dataFolder.resolve(CONFIG_FILE);
        try {
            Files.createDirectories(dataFolder);
            if (!Files.exists(file)) {
                copyBundled(file);
            }
            writeReferenceCopy(dataFolder);
            try (Reader reader = Files.newBufferedReader(file)) {
                return from(YamlConfiguration.loadConfiguration(reader));
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to read " + CONFIG_FILE, ex);
        }
    }

    /**
     * Builds the settings from a loaded {@code config.yml}.
     *
     * <p>Defaults to enabled, so an operator whose file predates this setting keeps the behaviour
     * they already had rather than silently losing mail delivery on upgrade.</p>
     */
    static @NotNull EssentialsAdapterConfig from(@NotNull YamlConfiguration config) {
        Objects.requireNonNull(config, "config");
        return new EssentialsAdapterConfig(config.getBoolean(NOTIFICATIONS_ENABLED, true));
    }

    /**
     * Writes {@code defaults/default-config.yml}, overwriting any previous copy. Rewritten on every
     * start so it always shows what a current file looks like; never read back.
     */
    public static void writeReferenceCopy(@NotNull Path dataFolder) throws IOException {
        Path defaults = dataFolder.resolve(DEFAULTS_DIR);
        Files.createDirectories(defaults);
        copyBundled(defaults.resolve(REFERENCE_FILE));
    }

    private static void copyBundled(@NotNull Path target) throws IOException {
        try (InputStream bundled = EssentialsAdapterConfig.class.getClassLoader()
                .getResourceAsStream(CONFIG_FILE)) {
            if (bundled == null) {
                throw new IllegalStateException(
                        "essentials-adapter jar is missing its bundled " + CONFIG_FILE);
            }
            Files.copy(bundled, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
