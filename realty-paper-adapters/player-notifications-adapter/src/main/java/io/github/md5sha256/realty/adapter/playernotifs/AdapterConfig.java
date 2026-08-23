package io.github.md5sha256.realty.adapter.playernotifs;

import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Objects;

/**
 * Reads the module's {@code config.yml}.
 *
 * <p>All this file holds is {@code expiry-days}. Categories used to live beside it in a
 * {@code categories.yml}; they are now registered in code and presented from PlayerNotifications' own
 * {@code categories.yml} — see {@link RealtyCategory}. Expiry stays here because it is neither a
 * category nor something PlayerNotifications can infer: it is this adapter's choice of how long a
 * Realty notification is worth keeping.</p>
 *
 * <p>Kept out of {@link PlayerNotificationsAdapterModule} so it can be tested directly: the module
 * extends {@code SimplePluginModule}, and reaching it from a test would drag
 * {@code plugin-infrastructure} — a {@code compileOnly} dependency — onto the test classpath. Only
 * Bukkit's own config classes are needed here, and those run without a server.</p>
 */
public final class AdapterConfig {

    static final String CONFIG_FILE = "config.yml";
    /** See the project config rules: every operator config ships a regenerated reference copy. */
    static final String DEFAULTS_DIR = "defaults";
    static final String REFERENCE_FILE = "default-config.yml";

    private static final String EXPIRY_DAYS = "expiry-days";
    private static final int DEFAULT_EXPIRY_DAYS = 30;

    private final Duration expiry;

    AdapterConfig(@NotNull Duration expiry) {
        this.expiry = Objects.requireNonNull(expiry, "expiry");
    }

    /** How long an enqueued notification survives before PlayerNotifications expires it. */
    public @NotNull Duration expiry() {
        return this.expiry;
    }

    /**
     * Reads the operator's {@code config.yml}, writing the bundled default there first if they have
     * none, and refreshing the reference copy beside it either way.
     */
    public static @NotNull AdapterConfig read(@NotNull Path dataFolder) {
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
     * Builds the settings from a loaded {@code config.yml}, defaulting {@code expiry-days} so a file
     * predating this setting — or an operator's file that never had it — still starts.
     */
    static @NotNull AdapterConfig from(@NotNull YamlConfiguration config) {
        Objects.requireNonNull(config, "config");
        return new AdapterConfig(
                Duration.ofDays(config.getLong(EXPIRY_DAYS, DEFAULT_EXPIRY_DAYS)));
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
        try (InputStream bundled = AdapterConfig.class.getClassLoader()
                .getResourceAsStream(CONFIG_FILE)) {
            if (bundled == null) {
                throw new IllegalStateException(
                        "player-notifications-adapter jar is missing its bundled " + CONFIG_FILE);
            }
            Files.copy(bundled, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
