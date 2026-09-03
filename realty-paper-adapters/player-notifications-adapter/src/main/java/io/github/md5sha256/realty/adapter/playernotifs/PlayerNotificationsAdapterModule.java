package io.github.md5sha256.realty.adapter.playernotifs;

import com.minecraftcitiesnetwork.pluginInfrastructure.modules.PluginModule;
import com.minecraftcitiesnetwork.pluginInfrastructure.modules.SimplePluginModule;
import io.github.md5sha256.realty.Realty;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * Delivers Realty notifications through the PlayerNotifications plugin, so players get per-category
 * preferences, sink fan-out, offline delivery and an inbox instead of the all-or-nothing online
 * chat delivery {@code chat-adapter} provides.
 *
 * <p>Categories are registered in code from {@link RealtyCategory} and presented from
 * PlayerNotifications' own {@code categories.yml}; this module's {@code config.yml} holds only
 * {@code expiry-days}.</p>
 *
 * <p><b>This class must name no PlayerNotifications type — not in a field, a method signature, an
 * import, or a lambda.</b> {@code ModuleLoader} loads it with {@code Class.forName}, and the JVM
 * resolves the types a class names while loading and verifying it, long before {@link #initialize}
 * can check whether PlayerNotifications is installed. A single such reference therefore threw
 * {@code NoClassDefFoundError} out of {@code ModuleLifecycleManager.start} — an {@code Error} it
 * does not catch — and took Realty's entire {@code onEnable} down on servers without the plugin.
 * All of that state lives in {@link PlayerNotificationsBridge}, which is in this module's own jar
 * and is first mentioned only after the presence check has passed.</p>
 */
public final class PlayerNotificationsAdapterModule extends SimplePluginModule<Realty> {

    private @Nullable PlayerNotificationsBridge bridge;

    /**
     * {@inheritDoc}
     *
     * <p>The presence check comes first and everything PlayerNotifications-typed comes second, via
     * {@link PlayerNotificationsBridge}; see this class's own documentation for why that ordering is
     * load-bearing rather than stylistic.</p>
     *
     * <p>As on {@code EssentialsAdapterModule}, the failure is signalled unchecked:
     * {@code SimplePluginModule.initialize} declares no checked exception and an override may only
     * narrow a throws clause. The lifecycle manager catches
     * {@code ModuleInitializationException | RuntimeException} identically (logs severe, unloads
     * this module only).</p>
     *
     * @throws IllegalStateException if PlayerNotifications is absent, disabled, or has not
     *                               registered its service
     */
    @Override
    public void initialize(@NotNull Realty plugin, @NotNull Path dataFolder) {
        super.initialize(plugin, dataFolder);

        Plugin pnPlugin = Bukkit.getPluginManager().getPlugin("PlayerNotifications");
        if (pnPlugin == null || !pnPlugin.isEnabled()) {
            throw new IllegalStateException(
                    "PlayerNotifications is not installed or not enabled — player-notifications-adapter cannot start");
        }
        this.bridge = PlayerNotificationsBridge.start(plugin, dataFolder, this::registerListener);
    }

    /**
     * Re-reads {@code config.yml} and {@code titles.yml} and pushes them into the running renderer
     * and listener.
     *
     * <p>Without this override {@code /realty reload} did nothing here: the manifest's
     * {@code reloadable: true} only permits a reload, and {@link PluginModule#reload}
     * defaults to a no-op, so both files were read once in {@link #initialize} and never again.</p>
     *
     * <p>The reads happen on the calling thread rather than being pushed onto a background one:
     * {@code /realty reload} already runs off the main thread, and two small YAML files are not
     * worth a thread hop that would only add a race against a concurrent shutdown.</p>
     */
    @Override
    public @NotNull CompletableFuture<Void> reload(@NotNull Realty plugin) {
        PlayerNotificationsBridge activeBridge = this.bridge;
        if (activeBridge == null) {
            // Not initialized, or already shut down: nothing live to refresh.
            return CompletableFuture.completedFuture(null);
        }
        try {
            activeBridge.reload(dataFolder());
        } catch (RuntimeException e) {
            // The module keeps running on its previous config; a broken edit must not take it down.
            return CompletableFuture.failedFuture(e);
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void shutdown(@NotNull Realty plugin) {
        unregisterListeners();
        PlayerNotificationsBridge activeBridge = this.bridge;
        if (activeBridge != null) {
            activeBridge.shutdown();
            this.bridge = null;
        }
        super.shutdown(plugin);
    }
}
