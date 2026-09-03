package io.github.md5sha256.realty.adapter.query;

import com.minecraftcitiesnetwork.pluginInfrastructure.modules.SimplePluginModule;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import io.github.md5sha256.realty.Realty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * Runs the private query endpoint {@code realty-rest} calls for live geometry and player names.
 *
 * <p>Registers no commands: modules start after commands are registered and Paper accepts no new
 * Brigadier commands at that point. Has no write path.</p>
 *
 * <p>A thin adapter over {@link ServerLifecycle}, which holds the start/stop/reload behaviour in a
 * Bukkit-free form so it can be tested.</p>
 */
public final class QueryServiceModule extends SimplePluginModule<Realty> {

    private @Nullable ServerLifecycle lifecycle;

    @Override
    public void initialize(@NotNull Realty plugin, @NotNull Path dataFolder) {
        super.initialize(plugin, dataFolder);
        this.lifecycle = new ServerLifecycle(
                () -> QueryServiceConfig.read(dataFolder),
                config -> serve(plugin, config),
                plugin.getLogger());
        this.lifecycle.start();
    }

    @Override
    public void shutdown(@NotNull Realty plugin) {
        if (this.lifecycle != null) {
            this.lifecycle.stop();
            this.lifecycle = null;
        }
        super.shutdown(plugin);
    }

    /**
     * A reload re-reads {@code config.yml} and restarts the server on the new settings.
     * {@code reloadable: true} in the manifest does nothing by itself; this override is what makes
     * {@code /realty module reload query-service} take effect.
     */
    @Override
    public @NotNull CompletableFuture<Void> reload(@NotNull Realty plugin) {
        if (this.lifecycle != null) {
            this.lifecycle.reload();
        }
        return CompletableFuture.completedFuture(null);
    }

    private static @NotNull AutoCloseable serve(@NotNull Realty plugin,
                                                @NotNull QueryServiceConfig config) {
        // Composition root: this is the only place in the module that reaches for a static service
        // locator. Everything below is handed its collaborators.
        RegionContainer regionContainer = WorldGuard.getInstance().getPlatform().getRegionContainer();
        QueryServiceServer server = new QueryServiceServer(
                config.sharedSecret(),
                config.requestTimeout(),
                new MainThreadDimensionsSource(
                        plugin.executorState().mainThreadExec(),
                        plugin.getServer()::getWorld,
                        world -> regionContainer.get(BukkitAdapter.adapt(world))),
                plugin.paperApi().playerNameService());
        server.start(config.bindHost(), config.port());
        return server::stop;
    }
}
