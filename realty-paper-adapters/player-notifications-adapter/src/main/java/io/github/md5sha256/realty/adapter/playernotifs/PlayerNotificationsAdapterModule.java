package io.github.md5sha256.realty.adapter.playernotifs;

import com.minecraftcitiesnetwork.pluginInfrastructure.modules.SimplePluginModule;
import io.github.md5sha256.playernotifications.api.NotificationService;
import io.github.md5sha256.realty.Realty;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Delivers Realty notifications through the PlayerNotifications plugin, so players get per-category
 * preferences, sink fan-out, offline delivery and an inbox instead of the all-or-nothing online
 * chat delivery {@code chat-adapter} provides.
 */
public final class PlayerNotificationsAdapterModule extends SimplePluginModule<Realty> {

    private @Nullable NotificationService service;
    /**
     * The mapper the live registrations were made from — never re-read on shutdown. See
     * {@link RealtyDataTypes}: tearing down with a mapper built from a newer {@code categories.yml}
     * would orphan any data type the operator removed in between.
     */
    private @Nullable NotificationCategoryMapper registeredMapper;

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
     * @throws IllegalStateException    if PlayerNotifications is absent, disabled, or has not
     *                                  registered its service
     * @throws IllegalArgumentException if {@code categories.yml} is not a usable category set
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

        // 2. Load the operator's category set. This decides which data types exist, not just how
        //    message keys route between them.
        YamlConfiguration config = CategoriesConfig.read(dataFolder, plugin.getLogger());
        NotificationCategoryMapper categoryMapper = CategoriesConfig.readMapper(config);
        Duration expiry = CategoriesConfig.readExpiry(config);

        // 3. Register payload types, renderers and categories.
        RealtyDataTypes.registerAll(
                notificationService, categoryMapper, new RealtyNotificationRenderer(categoryMapper));
        this.service = notificationService;
        this.registeredMapper = categoryMapper;

        // 4. Only now, with nothing left that can throw, does a live listener appear.
        registerListener(new PlayerNotificationsListener(
                notificationService::enqueueNotification,
                categoryMapper,
                expiry,
                plugin.getLogger()));
    }

    @Override
    public void shutdown(@NotNull Realty plugin) {
        unregisterListeners();
        NotificationService notificationService = this.service;
        NotificationCategoryMapper mapper = this.registeredMapper;
        if (notificationService != null && mapper != null) {
            // The whole set, never a subset — see RealtyDataTypes for why a partial unregister
            // silently corrupts the registry for the data types left behind.
            RealtyDataTypes.unregisterAll(notificationService.dataTypeRegistry(), mapper);
            RealtyDataTypes.unclaimAll(notificationService.categoryRegistry(), mapper);
            this.service = null;
            this.registeredMapper = null;
        }
        super.shutdown(plugin);
    }
}
