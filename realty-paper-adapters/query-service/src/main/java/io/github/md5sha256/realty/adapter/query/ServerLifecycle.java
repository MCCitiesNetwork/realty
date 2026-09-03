package io.github.md5sha256.realty.adapter.query;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Start/stop/reload for the query endpoint, with no Bukkit in sight.
 *
 * <p>Extracted from {@code QueryServiceModule} so this — the part with the interesting failure
 * behaviour — can be tested. The module extends {@code SimplePluginModule}, and reaching it from a
 * test would drag {@code plugin-infrastructure} and the Paper API onto the test classpath; the
 * config reader and the server factory are supplied instead, so a test can make either fail on
 * demand.</p>
 */
final class ServerLifecycle {

    private final Supplier<QueryServiceConfig> configReader;
    private final Function<QueryServiceConfig, AutoCloseable> serverFactory;
    private final Logger log;

    private @Nullable AutoCloseable server;
    private @Nullable QueryServiceConfig activeConfig;

    /**
     * @param configReader  reads the operator's configuration; may throw
     * @param serverFactory starts a server on a configuration and returns a handle whose
     *                      {@code close()} stops it; may throw
     */
    ServerLifecycle(@NotNull Supplier<QueryServiceConfig> configReader,
                    @NotNull Function<QueryServiceConfig, AutoCloseable> serverFactory,
                    @NotNull Logger log) {
        this.configReader = Objects.requireNonNull(configReader, "configReader");
        this.serverFactory = Objects.requireNonNull(serverFactory, "serverFactory");
        this.log = Objects.requireNonNull(log, "log");
    }

    void start() {
        start(this.configReader.get());
    }

    /**
     * Re-reads the configuration and restarts on it.
     *
     * <p>A broken edit keeps the previous configuration running: if the new configuration fails to
     * read the running server is left alone entirely, and if the new one fails to start (a port
     * clash, say) the module falls back to what it was already running rather than being left
     * without a server until another reload succeeds. Only a failure of that fallback too
     * propagates.</p>
     */
    void reload() {
        QueryServiceConfig newConfig;
        try {
            newConfig = this.configReader.get();
        } catch (RuntimeException e) {
            this.log.log(Level.SEVERE, "query-service: reload failed to read config.yml, keeping the "
                    + "running configuration", e);
            return;
        }
        QueryServiceConfig previousConfig = this.activeConfig;
        stop();
        try {
            start(newConfig);
        } catch (RuntimeException e) {
            if (previousConfig == null) {
                throw e;
            }
            this.log.log(Level.SEVERE, "query-service: failed to start on the new config, "
                    + "restarting on the previous one", e);
            start(previousConfig);
        }
    }

    void stop() {
        AutoCloseable running = this.server;
        this.server = null;
        if (running == null) {
            return;
        }
        try {
            running.close();
        } catch (Exception e) {
            this.log.log(Level.SEVERE, "query-service: failed to stop the endpoint cleanly", e);
        }
    }

    private void start(@NotNull QueryServiceConfig config) {
        if (!config.httpEnabled()) {
            this.log.warning("query-service: shared-secret is empty, so the query endpoint is NOT "
                    + "running. realty-rest will serve null geometry and null player names until a "
                    + "secret is set here and matched in REALTY_REST_MODULE_SECRET.");
            this.activeConfig = config;
            return;
        }
        this.server = this.serverFactory.apply(config);
        this.activeConfig = config;
        this.log.info("query-service listening on http://" + config.bindHost() + ":" + config.port());
    }
}
