package io.github.md5sha256.realty.adapter.query;

import com.minecraftcitiesnetwork.pluginInfrastructure.modules.SimplePluginModule;
import io.github.md5sha256.realty.Realty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runs the private query endpoint {@code realty-rest} calls for live geometry and player names.
 *
 * <p>Registers no commands: modules start after commands are registered and Paper accepts no new
 * Brigadier commands at that point. Has no write path.</p>
 */
public final class QueryServiceModule extends SimplePluginModule<Realty> {

    private @Nullable QueryServiceServer server;
    private @Nullable QueryServiceConfig activeConfig;

    @Override
    public void initialize(@NotNull Realty plugin, @NotNull Path dataFolder) {
        super.initialize(plugin, dataFolder);
        start(plugin, QueryServiceConfig.read(dataFolder));
    }

    @Override
    public void shutdown(@NotNull Realty plugin) {
        stop();
        super.shutdown(plugin);
    }

    /**
     * A reload re-reads {@code config.yml} and restarts the server on the new settings.
     * {@code reloadable: true} in the manifest does nothing by itself; this override is what makes
     * {@code /realty module reload query-service} take effect.
     *
     * <p>A broken edit keeps the previous configuration running: if the new {@code config.yml}
     * fails to read, or the new server fails to start (e.g. a port clash on the new bind settings),
     * the module falls back to the configuration it was already running rather than being left
     * without a server until another reload succeeds.</p>
     */
    @Override
    public @NotNull CompletableFuture<Void> reload(@NotNull Realty plugin) {
        Logger log = plugin.getLogger();
        QueryServiceConfig newConfig;
        try {
            newConfig = QueryServiceConfig.read(dataFolder());
        } catch (RuntimeException e) {
            log.log(Level.SEVERE, "query-service: reload failed to read config.yml, keeping the "
                    + "running configuration", e);
            return CompletableFuture.completedFuture(null);
        }
        QueryServiceConfig previousConfig = this.activeConfig;
        stop();
        try {
            start(plugin, newConfig);
        } catch (RuntimeException e) {
            if (previousConfig != null) {
                log.log(Level.SEVERE, "query-service: failed to start on the new config, "
                        + "restarting on the previous one", e);
                start(plugin, previousConfig);
            } else {
                throw e;
            }
        }
        return CompletableFuture.completedFuture(null);
    }

    private void start(@NotNull Realty plugin, @NotNull QueryServiceConfig config) {
        Logger log = plugin.getLogger();
        if (!config.httpEnabled()) {
            log.warning("query-service: shared-secret is empty in " + dataFolder().resolve("config.yml")
                    + ", so the query endpoint is NOT running. realty-rest will serve null geometry and "
                    + "null player names until a secret is set here and matched in REALTY_REST_MODULE_SECRET.");
            this.activeConfig = config;
            return;
        }
        QueryServiceServer created = new QueryServiceServer(
                config.sharedSecret(),
                config.requestTimeout(),
                new MainThreadDimensionsSource(plugin.executorState().mainThreadExec()),
                plugin.paperApi().playerNameService());
        created.start(config.bindHost(), config.port());
        this.server = created;
        this.activeConfig = config;
        log.info("query-service listening on http://" + config.bindHost() + ":" + config.port());
    }

    private void stop() {
        QueryServiceServer running = this.server;
        this.server = null;
        if (running != null) {
            running.stop();
        }
    }
}
