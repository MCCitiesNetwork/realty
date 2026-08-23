package io.github.md5sha256.realty.adapter.playernotifs;

import com.minecraftcitiesnetwork.pluginInfrastructure.modules.PluginModule;
import com.minecraftcitiesnetwork.pluginInfrastructure.modules.SimplePluginModule;
import io.github.md5sha256.playernotifications.api.NotificationService;
import io.github.md5sha256.realty.Realty;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Delivers Realty notifications through the PlayerNotifications plugin, so players get per-category
 * preferences, sink fan-out, offline delivery and an inbox instead of the all-or-nothing online
 * chat delivery {@code chat-adapter} provides.
 *
 * <p>Categories are registered in code from {@link RealtyCategory} and presented from
 * PlayerNotifications' own {@code categories.yml}; this module's {@code config.yml} holds only
 * {@code expiry-days}.</p>
 */
public final class PlayerNotificationsAdapterModule extends SimplePluginModule<Realty> {

    /** The pre-1.5.0 category config, read by nothing since categories moved into code. */
    static final String OBSOLETE_CATEGORIES_FILE = "categories.yml";

    private @Nullable NotificationService service;
    private @Nullable RealtyNotificationRenderer renderer;
    private @Nullable PlayerNotificationsListener listener;

    /**
     * {@inheritDoc}
     *
     * <p><b>Order matters: every fallible step happens before {@code registerListener}.</b> If
     * anything thrown after the listener is registered escapes this method,
     * {@code ModuleLifecycleManager} closes the module's class loader <em>without</em> calling
     * {@link #shutdown}, so the listener is never unregistered and stays live on a dead class
     * loader — every subsequent notification then dies in a {@code NoClassDefFoundError} inside
     * Bukkit's event dispatch. Registering the listener last makes the failure path clean: nothing
     * is live, so nothing needs unwinding.</p>
     *
     * <p>As on {@code EssentialsAdapterModule}, the failure is signalled unchecked:
     * {@code SimplePluginModule.initialize} declares no checked exception and an override may only
     * narrow a throws clause. The lifecycle manager catches
     * {@code ModuleInitializationException | RuntimeException} identically.</p>
     *
     * @throws IllegalStateException if PlayerNotifications is absent, disabled, or has not
     *                               registered its service
     */
    @Override
    public void initialize(@NotNull Realty plugin, @NotNull Path dataFolder) {
        super.initialize(plugin, dataFolder);

        // 1. PlayerNotifications registers NotificationService in its own onEnable; a null here
        //    means PN is missing, disabled, or started after us.
        Plugin pnPlugin = Bukkit.getPluginManager().getPlugin("PlayerNotifications");
        if (pnPlugin == null || !pnPlugin.isEnabled()) {
            throw new IllegalStateException(
                    "PlayerNotifications is not installed or not enabled — player-notifications-adapter cannot start");
        }
        NotificationService notificationService =
                Bukkit.getServicesManager().load(NotificationService.class);
        if (notificationService == null) {
            throw new IllegalStateException(
                    "PlayerNotifications is enabled but registered no NotificationService — "
                            + "player-notifications-adapter cannot start");
        }

        // 2. Read expiry and the operator's row titles. The category set is compiled in, so nothing
        //    about it can fail here.
        AdapterConfig config = AdapterConfig.read(dataFolder);
        TitleConfig titles = TitleConfig.read(dataFolder);
        warnAboutObsoleteCategoriesFile(plugin, dataFolder);

        // 3. Register payload types, renderers and categories. PN's registry notifies its own
        //    change listener, so registering this late still reaches the preference dialogs.
        RealtyNotificationRenderer notificationRenderer = new RealtyNotificationRenderer(titles);
        RealtyDataTypes.registerAll(notificationService, notificationRenderer);
        this.service = notificationService;
        this.renderer = notificationRenderer;

        // 4. Only now, with nothing left that can throw, does a live listener appear.
        PlayerNotificationsListener notificationListener = new PlayerNotificationsListener(
                notificationService::enqueueNotification, config.expiry(), plugin.getLogger());
        registerListener(notificationListener);
        this.listener = notificationListener;
    }

    /**
     * Re-reads {@code config.yml} and {@code titles.yml} and pushes them into the running renderer
     * and listener.
     *
     * <p>Without this override {@code /realty reload} did nothing here: the manifest's
     * {@code reloadable: true} only permits a reload, and {@link PluginModule#reload}
     * defaults to a no-op, so both files were read once in {@link #initialize} and never again.</p>
     *
     * <p>Nothing is re-registered with PlayerNotifications. The renderer instance PN holds and the
     * listener Bukkit holds both stay exactly as they are; only the config each reads is swapped.
     * Re-registering would mean an {@code unregisterAll}/{@code registerAll} cycle on PN's registry,
     * which {@link RealtyDataTypes} documents as corrupting the registry if it is ever partial —
     * a risk this refresh has no reason to take.</p>
     *
     * <p>The reads happen on the calling thread rather than being pushed onto a background one:
     * {@code /realty reload} already runs off the main thread, and two small YAML files are not
     * worth a thread hop that would only add a race against a concurrent shutdown.</p>
     */
    @Override
    public @NotNull CompletableFuture<Void> reload(@NotNull Realty plugin) {
        RealtyNotificationRenderer notificationRenderer = this.renderer;
        PlayerNotificationsListener notificationListener = this.listener;
        if (notificationRenderer == null || notificationListener == null) {
            // Not initialized, or already shut down: nothing live to refresh.
            return CompletableFuture.completedFuture(null);
        }
        try {
            notificationRenderer.setTitles(TitleConfig.read(dataFolder()));
            notificationListener.setExpiry(AdapterConfig.read(dataFolder()).expiry());
        } catch (RuntimeException e) {
            // The module keeps running on its previous config; a broken edit must not take it down.
            return CompletableFuture.failedFuture(e);
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Tells an upgrading operator that their {@code categories.yml} is now inert.
     *
     * <p>The file is left on disk rather than deleted or backed up. It is the operator's, it holds
     * the grouping they meant, and that grouping is exactly what they need in front of them while
     * they re-enter it in PlayerNotifications' {@code categories.yml}. Tidying it away is not this
     * module's call.</p>
     */
    private void warnAboutObsoleteCategoriesFile(@NotNull Realty plugin, @NotNull Path dataFolder) {
        if (!Files.exists(dataFolder.resolve(OBSOLETE_CATEGORIES_FILE))) {
            return;
        }
        plugin.getLogger().log(Level.INFO,
                "{0} in this module''s folder is no longer read: Realty now registers its notification "
                        + "categories with PlayerNotifications, which presents them from its own "
                        + "categories.yml. Copy the grouping you want out of the blocks PlayerNotifications "
                        + "writes to categories-defaults.yml, then delete this file.",
                OBSOLETE_CATEGORIES_FILE);
    }

    @Override
    public void shutdown(@NotNull Realty plugin) {
        unregisterListeners();
        NotificationService notificationService = this.service;
        if (notificationService != null) {
            // The whole set, never a subset — see RealtyDataTypes for why a partial unregister
            // silently corrupts the registry for the data types left behind. The set is compile-time
            // constant, so unlike the config-driven version this can no longer drift across a reload.
            RealtyDataTypes.unregisterAll(notificationService.dataTypeRegistry());
            RealtyDataTypes.unclaimAll(notificationService.categoryRegistry());
            this.service = null;
        }
        this.renderer = null;
        this.listener = null;
        super.shutdown(plugin);
    }
}
