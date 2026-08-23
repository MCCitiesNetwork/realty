package io.github.md5sha256.realty.adapter.playernotifs;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Reads the module's {@code titles.yml}: the operator's overrides for the short summary a Realty
 * notification renders with.
 *
 * <p>Titles are their own file rather than a block in {@code config.yml} because they are the one
 * thing here an operator edits in bulk — the bundled copy lists every message key Realty can fire, so
 * a single setting like {@code expiry-days} would be buried under sixty lines of rows.</p>
 *
 * <p><b>Overrides are sparse in effect even though the shipped file is complete.</b> A key present
 * here wins; a key absent falls back to {@link RealtyCategory#titleFor}. That is what keeps an
 * operator's file from freezing the titles at the version they last copied: a key added by a newer
 * Realty simply is not in their file, and renders from the compiled table until they choose otherwise.</p>
 *
 * <p>Values are MiniMessage so a row can be coloured. A blank value falls back rather than rendering
 * an empty row — PlayerNotifications lists a row by its title alone, so an empty title is a row a
 * player cannot read at all.</p>
 *
 * <p>Free of PlayerNotifications and {@code plugin-infrastructure} types for the same reason
 * {@link AdapterConfig} is: only Bukkit's config classes are needed, and those run without a server.</p>
 */
public final class TitleConfig {

    static final String TITLES_FILE = "titles.yml";
    /** See the project config rules: every operator config ships a regenerated reference copy. */
    static final String REFERENCE_FILE = "default-titles.yml";

    private static final String TITLES_SECTION = "titles";

    /** Overrides nothing; every key renders from {@link RealtyCategory}. */
    private static final TitleConfig COMPILED = new TitleConfig(Map.of());

    /**
     * Bukkit splits a dotted path into nested sections at load time. Message keys are dotted
     * ({@code notification.leasehold-expired}), so the separator is neutralised before loading and
     * every key stays whole.
     */
    private static final char NO_PATH_SEPARATOR = '\u0000';

    private final Map<String, Component> overrides;

    private TitleConfig(@NotNull Map<String, Component> overrides) {
        this.overrides = Map.copyOf(overrides);
    }

    /**
     * The title a notification for the given message key renders with: the operator's override if
     * they wrote one, otherwise the compiled summary from {@link RealtyCategory}.
     */
    public @NotNull Component titleFor(@NotNull String messageKey) {
        Objects.requireNonNull(messageKey, "messageKey");
        Component override = this.overrides.get(messageKey);
        return override != null ? override : Component.text(RealtyCategory.titleFor(messageKey));
    }

    /**
     * Overrides nothing — every key renders at its compiled title. Used where no operator file is in
     * play, chiefly by tests asserting the defaults.
     */
    public static @NotNull TitleConfig compiled() {
        return COMPILED;
    }

    /** The message keys this file overrides, whether or not a category claims them. */
    public @NotNull Set<String> overriddenKeys() {
        return this.overrides.keySet();
    }

    /**
     * Reads the operator's {@code titles.yml}, writing the bundled default there first if they have
     * none, and refreshing the reference copy beside it either way.
     */
    public static @NotNull TitleConfig read(@NotNull Path dataFolder) {
        Objects.requireNonNull(dataFolder, "dataFolder");
        Path file = dataFolder.resolve(TITLES_FILE);
        try {
            Files.createDirectories(dataFolder);
            if (!Files.exists(file)) {
                copyBundled(file);
            }
            writeReferenceCopy(dataFolder);
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                return load(reader);
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to read " + TITLES_FILE, ex);
        }
    }

    /**
     * Writes {@code defaults/default-titles.yml}, overwriting any previous copy. Rewritten on every
     * start so it always shows what a current file looks like; never read back.
     */
    public static void writeReferenceCopy(@NotNull Path dataFolder) throws IOException {
        Path defaults = dataFolder.resolve(AdapterConfig.DEFAULTS_DIR);
        Files.createDirectories(defaults);
        copyBundled(defaults.resolve(REFERENCE_FILE));
    }

    private static void copyBundled(@NotNull Path target) throws IOException {
        try (InputStream bundled = TitleConfig.class.getClassLoader()
                .getResourceAsStream(TITLES_FILE)) {
            if (bundled == null) {
                throw new IllegalStateException(
                        "player-notifications-adapter jar is missing its bundled " + TITLES_FILE);
            }
            Files.copy(bundled, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Reads a {@code titles.yml} document, keeping its dotted message keys whole. */
    public static @NotNull TitleConfig load(@NotNull Reader reader) throws IOException {
        Objects.requireNonNull(reader, "reader");
        YamlConfiguration config = new YamlConfiguration();
        config.options().pathSeparator(NO_PATH_SEPARATOR);
        try {
            config.load(reader);
        } catch (InvalidConfigurationException ex) {
            throw new IOException("Failed to parse " + TITLES_FILE, ex);
        }
        return from(config);
    }

    /**
     * Builds the overrides from a loaded document. The document must have been loaded with the path
     * separator neutralised, or its message keys arrive here already split into sections.
     */
    static @NotNull TitleConfig from(@NotNull YamlConfiguration config) {
        Objects.requireNonNull(config, "config");
        ConfigurationSection section = config.getConfigurationSection(TITLES_SECTION);
        if (section == null) {
            return new TitleConfig(Map.of());
        }
        Map<String, Component> overrides = new HashMap<>();
        for (String messageKey : section.getKeys(false)) {
            String value = section.getString(messageKey);
            if (value == null || value.isBlank()) {
                continue;
            }
            overrides.put(messageKey, MiniMessage.miniMessage().deserialize(value));
        }
        return new TitleConfig(overrides);
    }
}
