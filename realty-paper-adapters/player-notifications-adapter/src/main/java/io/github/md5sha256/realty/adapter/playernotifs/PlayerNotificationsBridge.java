package io.github.md5sha256.realty.adapter.playernotifs;

import io.github.md5sha256.playernotifications.api.NotificationService;
import io.github.md5sha256.realty.Realty;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Holds every piece of this module that is typed against the PlayerNotifications API.
 *
 * <p><b>This split exists for class loading, not for tidiness.</b> {@code ModuleLoader} resolves a
 * module's entry class with {@code Class.forName}, and the JVM resolves the types named in a class's
 * fields and method signatures while loading and verifying it. When
 * {@link PlayerNotificationsAdapterModule} held {@code NotificationService}, {@code
 * RealtyNotificationRenderer} and {@code PlayerNotificationsListener} fields directly, loading it on
 * a server without PlayerNotifications threw {@code NoClassDefFoundError} — an {@code Error}, which
 * {@code ModuleLifecycleManager} does not catch (it catches {@code ModuleInitializationException |
 * RuntimeException} around {@code initialize} only), so it escaped {@code start()} and took Realty's
 * whole {@code onEnable} down with it. The entry class now names no PlayerNotifications type at all,
 * so it loads on any server; this class is touched for the first time only after the entry class has
 * confirmed PlayerNotifications is present and enabled.</p>
 */
final class PlayerNotificationsBridge {

    /** The pre-1.5.0 category config, read by nothing since categories moved into code. */
    static final String OBSOLETE_CATEGORIES_FILE = "categories.yml";

    private final NotificationService service;
    private final RealtyNotificationRenderer renderer;
    private final PlayerNotificationsListener listener;

    private PlayerNotificationsBridge(@NotNull NotificationService service,
                                      @NotNull RealtyNotificationRenderer renderer,
                                      @NotNull PlayerNotificationsListener listener) {
        this.service = service;
        this.renderer = renderer;
        this.listener = listener;
    }

    /**
     * Loads config, registers Realty's data types and categories with PlayerNotifications, and hands
     * the finished listener to {@code listenerRegistrar}.
     *
     * <p><b>Order matters: every fallible step happens before the listener is registered.</b> If
     * anything thrown after registration escapes {@code initialize},
     * {@code ModuleLifecycleManager} closes the module's class loader <em>without</em> calling
     * {@code shutdown}, so the listener is never unregistered and stays live on a dead class
     * loader — every subsequent notification then dies in a {@code NoClassDefFoundError} inside
     * Bukkit's event dispatch. Registering the listener last makes the failure path clean: nothing
     * is live, so nothing needs unwinding.</p>
     *
     * @param plugin            the owning plugin, used for its logger
     * @param dataFolder        this module's data folder
     * @param listenerRegistrar receives the listener to register with Bukkit
     * @return the started bridge
     * @throws IllegalStateException if PlayerNotifications has registered no service
     */
    static @NotNull PlayerNotificationsBridge start(@NotNull Realty plugin,
                                                    @NotNull Path dataFolder,
                                                    @NotNull Consumer<Listener> listenerRegistrar) {
        // 1. PlayerNotifications registers NotificationService in its own onEnable; a null here
        //    means PN started after us, even though the plugin itself is enabled.
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

        // 4. Only now, with nothing left that can throw, does a live listener appear.
        PlayerNotificationsListener notificationListener = new PlayerNotificationsListener(
                notificationService::enqueueNotification, config.expiry(), plugin.getLogger());
        listenerRegistrar.accept(notificationListener);
        return new PlayerNotificationsBridge(notificationService, notificationRenderer,
                notificationListener);
    }

    /**
     * Re-reads {@code config.yml} and {@code titles.yml} and pushes them into the running renderer
     * and listener.
     *
     * <p>Nothing is re-registered with PlayerNotifications. The renderer instance PN holds and the
     * listener Bukkit holds both stay exactly as they are; only the config each reads is swapped.
     * Re-registering would mean an {@code unregisterAll}/{@code registerAll} cycle on PN's registry,
     * which {@link RealtyDataTypes} documents as corrupting the registry if it is ever partial —
     * a risk this refresh has no reason to take.</p>
     *
     * @param dataFolder this module's data folder
     * @throws RuntimeException if either file fails to parse
     */
    void reload(@NotNull Path dataFolder) {
        this.renderer.setTitles(TitleConfig.read(dataFolder));
        this.listener.setExpiry(AdapterConfig.read(dataFolder).expiry());
    }

    /**
     * Hands Realty's data types and category claims back to PlayerNotifications.
     *
     * <p>Unregistering the Bukkit listener is the entry class's job, since only a
     * {@code SimplePluginModule} can do it.</p>
     */
    void shutdown() {
        // The whole set, never a subset — see RealtyDataTypes for why a partial unregister
        // silently corrupts the registry for the data types left behind. The set is compile-time
        // constant, so unlike the config-driven version this can no longer drift across a reload.
        RealtyDataTypes.unregisterAll(this.service.dataTypeRegistry());
        RealtyDataTypes.unclaimAll(this.service.categoryRegistry());
    }

    /**
     * Tells an upgrading operator that their {@code categories.yml} is now inert.
     *
     * <p>The file is left on disk rather than deleted or backed up. It is the operator's, it holds
     * the grouping they meant, and that grouping is exactly what they need in front of them while
     * they re-enter it in PlayerNotifications' {@code categories.yml}. Tidying it away is not this
     * module's call.</p>
     */
    private static void warnAboutObsoleteCategoriesFile(@NotNull Realty plugin,
                                                        @NotNull Path dataFolder) {
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
}
