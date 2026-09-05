package io.github.md5sha256.realty.adapter.query;

import io.github.md5sha256.realty.adapter.query.json.ResourcePackAttribution;
import io.github.md5sha256.realty.adapter.query.json.ResourcePackEntry;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Reads the module's {@code config.yml}.
 *
 * <p>Kept out of {@code QueryServiceModule} so it can be tested directly: the module extends
 * {@code SimplePluginModule}, and reaching it from a test would drag {@code plugin-infrastructure} —
 * a {@code compileOnly} dependency — onto the test classpath.</p>
 */
public final class QueryServiceConfig {

    static final String CONFIG_FILE = "config.yml";
    /** See the project config rules: every operator config ships a regenerated reference copy. */
    static final String DEFAULTS_DIR = "defaults";
    static final String REFERENCE_FILE = "default-config.yml";

    private static final String SHARED_SECRET = "shared-secret";
    private static final String BIND_HOST = "bind-host";
    private static final String PORT = "port";
    private static final String REQUEST_TIMEOUT_MS = "request-timeout-ms";
    private static final String RESOURCE_PACKS = "resource-packs";

    private final String sharedSecret;
    private final String bindHost;
    private final int port;
    private final Duration requestTimeout;
    private final List<ResourcePackEntry> resourcePacks;

    QueryServiceConfig(@NotNull String sharedSecret,
                       @NotNull String bindHost,
                       int port,
                       @NotNull Duration requestTimeout,
                       @NotNull List<ResourcePackEntry> resourcePacks) {
        this.sharedSecret = sharedSecret;
        this.bindHost = bindHost;
        this.port = port;
        this.requestTimeout = requestTimeout;
        this.resourcePacks = List.copyOf(resourcePacks);
    }

    /**
     * Every configured pack, highest priority first, or empty when none is set.
     *
     * <p>The order is the operator's. The renderer resolves a texture two packs both
     * provide in favour of the earlier one, so an override pack is written above the base
     * pack it expects underneath it.</p>
     */
    public @NotNull List<ResourcePackEntry> resourcePacks() {
        return this.resourcePacks;
    }

    /** The shared secret; blank when unset. Blank means the HTTP server is not started. */
    public @NotNull String sharedSecret() {
        return this.sharedSecret;
    }

    public @NotNull String bindHost() {
        return this.bindHost;
    }

    public int port() {
        return this.port;
    }

    /** Cap on a main-thread round trip before a geometry request fails with 504. */
    public @NotNull Duration requestTimeout() {
        return this.requestTimeout;
    }

    /**
     * Whether the HTTP server runs at all. An empty secret fails closed rather than running open:
     * {@code realty-rest} degrades to nulls, which is safe, unlike an unauthenticated query port.
     */
    public boolean httpEnabled() {
        return !this.sharedSecret.isBlank();
    }

    /**
     * Reads the operator's {@code config.yml}, writing the bundled default there first if they have
     * none, and refreshing the reference copy beside it either way.
     */
    public static @NotNull QueryServiceConfig read(@NotNull Path dataFolder) {
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

    static @NotNull QueryServiceConfig from(@NotNull YamlConfiguration config) {
        Objects.requireNonNull(config, "config");
        return new QueryServiceConfig(
                config.getString(SHARED_SECRET, ""),
                config.getString(BIND_HOST, "127.0.0.1"),
                config.getInt(PORT, 8123),
                Duration.ofMillis(config.getLong(REQUEST_TIMEOUT_MS, 1000L)),
                resourcePacks(config.getList(RESOURCE_PACKS, List.of())));
    }

    /**
     * Reads {@code resource-packs}, highest priority first.
     *
     * <p>Each entry is either a bare URL string -- a pack with no credits -- or a map of
     * {@code url} and an optional {@code attribution} list in the same shape the single
     * pack's credits take. Credits belong to the entry rather than to the file because
     * two packs may be licensed differently.</p>
     *
     * <p>Empty means no pack, and a preview then renders untextured.</p>
     */
    private static @NotNull List<ResourcePackEntry> resourcePacks(@NotNull List<?> configured) {
        List<ResourcePackEntry> packs = new ArrayList<>(configured.size());
        for (Object entry : configured) {
            packs.add(resourcePack(entry));
        }
        // A pack listed twice has no defined priority, and one of the two entries can only
        // be a mistake. Failing names it; silently collapsing them would not.
        for (int i = 0; i < packs.size(); i++) {
            for (int j = i + 1; j < packs.size(); j++) {
                if (packs.get(i).url().equals(packs.get(j).url())) {
                    throw new IllegalStateException(RESOURCE_PACKS + " lists the same pack twice,"
                            + " so its priority is ambiguous: " + packs.get(i).url());
                }
            }
        }
        return packs;
    }

    private static @NotNull ResourcePackEntry resourcePack(@Nullable Object entry) {
        if (entry instanceof String url) {
            return new ResourcePackEntry(requirePackUrl(url), List.of());
        }
        if (entry instanceof Map<?, ?> map) {
            Object url = map.get("url");
            Object attribution = map.get("attribution");
            return new ResourcePackEntry(
                    requirePackUrl(url == null ? null : url.toString()),
                    attribution instanceof List<?> credits
                            ? resourcePackAttribution(credits)
                            : List.of());
        }
        throw new IllegalStateException(RESOURCE_PACKS
                + " entries must be a URL string or a url/attribution pair, was: " + entry);
    }

    private static @NotNull String requirePackUrl(@Nullable String configured) {
        String url = packUrl(configured);
        if (url == null) {
            throw new IllegalStateException(RESOURCE_PACKS
                    + " entries must have a url -- an entry with none names no pack");
        }
        return url;
    }

    /**
     * Reads the credit list.
     *
     * <p>Each entry is either a bare string -- the credit with no link -- or a map of
     * {@code text} and an optional {@code url}. A malformed entry fails the read rather
     * than being skipped: this setting exists so a required credit gets published, and
     * quietly dropping one would defeat the only reason to have it.</p>
     */
    private static @NotNull List<ResourcePackAttribution> resourcePackAttribution(
            @NotNull List<?> configured) {
        List<ResourcePackAttribution> credits = new ArrayList<>(configured.size());
        for (Object entry : configured) {
            if (entry instanceof String text) {
                credits.add(new ResourcePackAttribution(requireText(text), null));
            } else if (entry instanceof Map<?, ?> map) {
                Object text = map.get("text");
                Object url = map.get("url");
                credits.add(new ResourcePackAttribution(
                        requireText(text == null ? null : text.toString()),
                        attributionUrl(url == null ? null : url.toString())));
            } else {
                throw new IllegalStateException(RESOURCE_PACKS + " attribution"
                        + " entries must be a string or a text/url pair, was: " + entry);
            }
        }
        return credits;
    }

    private static @NotNull String requireText(@Nullable String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalStateException(RESOURCE_PACKS + " attribution"
                    + " entries must have text -- a credit with none would render as an"
                    + " empty line and credit nobody");
        }
        return text.trim();
    }

    /** Same rule as the pack URL, and for the same reason: the browser follows this link. */
    private static @Nullable String attributionUrl(@Nullable String configured) {
        if (configured == null || configured.isBlank()) {
            return null;
        }
        String trimmed = configured.trim();
        URI uri;
        try {
            uri = URI.create(trimmed);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    RESOURCE_PACKS + " attribution" + " has a link that is not a valid URL: " + trimmed, ex);
        }
        String scheme = uri.getScheme();
        if (scheme == null || uri.getHost() == null
                || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            throw new IllegalStateException(RESOURCE_PACKS + " attribution"
                    + " links must be absolute http(s) URLs, was: " + trimmed);
        }
        return trimmed;
    }

    /**
     * Validates the configured pack URL, or {@code null} when none is set.
     *
     * <p>Checked here rather than left to the browser: a path or a bare filename is an easy
     * mistake, and its only symptom would be a preview that never textures, with nothing
     * anywhere naming the cause. {@code file://} is rejected for the same reason -- the
     * fetch happens in the viewer's browser, not on the server, so a local path can never
     * resolve.</p>
     */
    private static @Nullable String packUrl(@Nullable String configured) {
        if (configured == null || configured.isBlank()) {
            return null;
        }
        String trimmed = configured.trim();
        URI uri;
        try {
            uri = URI.create(trimmed);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(RESOURCE_PACKS + " is not a valid URL: " + trimmed, ex);
        }
        String scheme = uri.getScheme();
        if (scheme == null || uri.getHost() == null) {
            throw new IllegalStateException(RESOURCE_PACKS
                    + " must be an absolute http(s) URL the browser can fetch, was: " + trimmed);
        }
        if (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https")) {
            throw new IllegalStateException(RESOURCE_PACKS
                    + " must use http or https -- the pack is fetched by the viewer's browser,"
                    + " not by the server -- was: " + trimmed);
        }
        return trimmed;
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
        try (InputStream bundled = QueryServiceConfig.class.getClassLoader()
                .getResourceAsStream(CONFIG_FILE)) {
            if (bundled == null) {
                throw new IllegalStateException("query-service jar is missing its bundled " + CONFIG_FILE);
            }
            Files.copy(bundled, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
