package io.github.md5sha256.realty.adapter.query;

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

    private final String sharedSecret;
    private final String bindHost;
    private final int port;
    private final Duration requestTimeout;

    QueryServiceConfig(@NotNull String sharedSecret,
                       @NotNull String bindHost,
                       int port,
                       @NotNull Duration requestTimeout) {
        this.sharedSecret = sharedSecret;
        this.bindHost = bindHost;
        this.port = port;
        this.requestTimeout = requestTimeout;
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
                Duration.ofMillis(config.getLong(REQUEST_TIMEOUT_MS, 1000L)));
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
