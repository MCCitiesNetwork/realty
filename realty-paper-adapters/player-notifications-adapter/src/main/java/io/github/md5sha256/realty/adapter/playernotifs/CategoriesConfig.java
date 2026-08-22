package io.github.md5sha256.realty.adapter.playernotifs;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
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
