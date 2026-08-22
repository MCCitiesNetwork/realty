package io.github.md5sha256.realty.adapter.playernotifs;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Parses the module's {@code categories.yml} into a {@link NotificationCategoryMapper}.
 *
 * <p>Kept out of {@link PlayerNotificationsAdapterModule} so the parse can be tested directly: the
 * module extends {@code SimplePluginModule}, and reaching it from a test would drag
 * {@code plugin-infrastructure} — a {@code compileOnly} dependency — onto the test classpath.
 * Only Bukkit's own config classes are needed here, and those run without a server.</p>
 */
public final class CategoriesConfig {

    static final String CATEGORIES_FILE = "categories.yml";
    /**
     * Where the always-current reference copy is written, mirroring the {@code defaults/} folder
     * {@code Realty} itself writes for {@code messages.yml} and friends.
     */
    static final String DEFAULTS_DIR = "defaults";
    static final String REFERENCE_FILE = "default-categories.yml";
    private static final String DEFAULT_FALLBACK = "realty.general";
    private static final int DEFAULT_EXPIRY_DAYS = 30;

    private CategoriesConfig() {
    }

    /**
     * Loads {@code categories.yml} with dots treated as ordinary characters rather than as path
     * separators.
     *
     * <p><b>Why this cannot be done on an already-loaded configuration.</b> Every key in this file
     * — {@code realty.auction}, {@code notification.outbid} — contains a dot, and Bukkit's default
     * path separator is a dot. {@code YamlConfiguration} applies the separator while <em>loading</em>
     * (each key is {@code set} by path), so {@code realty.auction: {...}} silently becomes a section
     * {@code realty} containing {@code auction}, and the top-level key the parser then reads back is
     * {@code realty}. Setting the separator after {@code loadConfiguration} is far too late — the
     * nesting has already happened. It is set here, on a configuration that has not read anything
     * yet, to a character a YAML key cannot contain.</p>
     *
     * @throws IllegalArgumentException if the YAML is malformed
     */
    public static @NotNull YamlConfiguration load(@NotNull Reader reader) {
        Objects.requireNonNull(reader, "reader");
        YamlConfiguration config = new YamlConfiguration();
        config.options().pathSeparator('\u0000');
        try {
            config.load(reader);
        } catch (InvalidConfigurationException ex) {
            throw new IllegalArgumentException(CATEGORIES_FILE + " is not valid YAML", ex);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to read " + CATEGORIES_FILE, ex);
        }
        return config;
    }

    /**
     * Reads the operator's {@code categories.yml}, writing the bundled default there first if they
     * have none, and refreshing the reference copy beside it either way.
     */
    public static @NotNull YamlConfiguration read(@NotNull Path dataFolder) {
        Objects.requireNonNull(dataFolder, "dataFolder");
        Path file = dataFolder.resolve(CATEGORIES_FILE);
        try {
            Files.createDirectories(dataFolder);
            if (!Files.exists(file)) {
                copyBundled(file);
            }
            writeReferenceCopy(dataFolder);
            try (Reader reader = Files.newBufferedReader(file)) {
                return load(reader);
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to read " + CATEGORIES_FILE, ex);
        }
    }

    /**
     * Writes {@code defaults/default-categories.yml}, overwriting any previous copy.
     *
     * <p>Rewritten on every start rather than only when absent: its whole purpose is to show what a
     * current, fully-populated file looks like, so an operator can diff their own against it after
     * an upgrade. A copy left over from an older version would answer that question wrongly, which
     * is worse than not being there at all. Nothing ever reads it back — only {@link
     * #CATEGORIES_FILE} is loaded — so editing it has no effect and clobbering it loses nothing.</p>
     */
    public static void writeReferenceCopy(@NotNull Path dataFolder) throws IOException {
        Path defaults = dataFolder.resolve(DEFAULTS_DIR);
        Files.createDirectories(defaults);
        copyBundled(defaults.resolve(REFERENCE_FILE));
    }

    private static void copyBundled(@NotNull Path target) throws IOException {
        try (InputStream bundled = CategoriesConfig.class.getClassLoader()
                .getResourceAsStream(CATEGORIES_FILE)) {
            if (bundled == null) {
                throw new IllegalStateException(
                        "player-notifications-adapter jar is missing its bundled " + CATEGORIES_FILE);
            }
            Files.copy(bundled, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Builds the mapper from a loaded {@code categories.yml}.
     *
     * <p>Category declaration order is the file's order, which {@code YamlConfiguration} preserves,
     * so the registration order an operator reads in the file is the one used at runtime.</p>
     *
     * @throws IllegalArgumentException if the file declares no usable category set
     */
    public static @NotNull NotificationCategoryMapper readMapper(@NotNull YamlConfiguration config) {
        Objects.requireNonNull(config, "config");
        ConfigurationSection section = config.getConfigurationSection("categories");
        if (section == null) {
            throw new IllegalArgumentException(
                    CATEGORIES_FILE + " has no 'categories' section; it must declare at least one category");
        }

        List<CategoryDefinition> categories = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) {
                throw new IllegalArgumentException(
                        "Category '" + key + "' in " + CATEGORIES_FILE + " is not a section. Since 1.4.2 a "
                                + "category declares its own label, description and keys; a bare "
                                + "'message-key: category' line is the pre-1.4.2 format.");
            }
            categories.add(new CategoryDefinition(
                    key,
                    orEmpty(entry.getString("label")),
                    orEmpty(entry.getString("description")),
                    orEmpty(entry.getString("title")),
                    entry.getInt("priority", 0),
                    List.copyOf(entry.getStringList("keys"))));
        }

        return new NotificationCategoryMapper(
                categories,
                readStrings(config.getConfigurationSection("title-overrides")),
                orDefault(config.getString("fallback-category"), DEFAULT_FALLBACK));
    }

    /** How long an enqueued notification survives before PlayerNotifications expires it. */
    public static @NotNull Duration readExpiry(@NotNull YamlConfiguration config) {
        return Duration.ofDays(
                Objects.requireNonNull(config, "config").getLong("expiry-days", DEFAULT_EXPIRY_DAYS));
    }

    private static @NotNull Map<String, String> readStrings(@Nullable ConfigurationSection section) {
        Map<String, String> values = new HashMap<>();
        if (section != null) {
            for (String key : section.getKeys(false)) {
                String value = section.getString(key);
                if (value != null && !value.isBlank()) {
                    values.put(key, value);
                }
            }
        }
        return values;
    }

    private static @NotNull String orEmpty(@Nullable String value) {
        return value == null ? "" : value;
    }

    private static @NotNull String orDefault(@Nullable String value, @NotNull String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
