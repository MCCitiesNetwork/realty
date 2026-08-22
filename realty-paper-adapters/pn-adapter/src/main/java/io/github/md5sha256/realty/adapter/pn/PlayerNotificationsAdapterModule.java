package io.github.md5sha256.realty.adapter.pn;

import com.minecraftcitiesnetwork.pluginInfrastructure.modules.SimplePluginModule;
import io.github.md5sha256.playernotifications.api.NotificationService;
import io.github.md5sha256.realty.Realty;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Delivers Realty notifications through the PlayerNotifications plugin, so players get per-category
 * preferences, sink fan-out, offline delivery and an inbox instead of the all-or-nothing online
 * chat delivery {@code chat-adapter} provides.
 */
public final class PlayerNotificationsAdapterModule extends SimplePluginModule<Realty> {

    private static final String CATEGORIES_FILE = "categories.yml";
    private static final int DEFAULT_EXPIRY_DAYS = 30;

    private @Nullable NotificationService service;

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
                    "PlayerNotifications is not installed or not enabled — pn-adapter cannot start");
        }
        NotificationService notificationService =
                Bukkit.getServicesManager().load(NotificationService.class);
        if (notificationService == null) {
            throw new IllegalStateException(
                    "PlayerNotifications is enabled but registered no NotificationService — "
                            + "pn-adapter cannot start");
        }

        // 2. Load the message-key -> dataType mapping from the module's data folder.
        YamlConfiguration config = loadCategoriesConfig(dataFolder);
        NotificationCategoryMapper categoryMapper = readMapper(config);
        Duration expiry = Duration.ofDays(config.getLong("expiry-days", DEFAULT_EXPIRY_DAYS));

        // 3. Register payload types, renderers and categories.
        RealtyDataTypes.registerAll(notificationService, new RealtyNotificationRenderer(categoryMapper));
        this.service = notificationService;

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
        if (notificationService != null) {
            // All five, never a subset — see RealtyDataTypes for why a partial unregister silently
            // corrupts the registry for the data types left behind.
            RealtyDataTypes.unregisterAll(notificationService.dataTypeRegistry());
            RealtyDataTypes.unclaimAll(notificationService.categoryRegistry());
            this.service = null;
        }
        super.shutdown(plugin);
    }

    /**
     * Reads {@code categories.yml} from the module's data folder, writing the bundled default there
     * first if the operator has none.
     */
    private static @NotNull YamlConfiguration loadCategoriesConfig(@NotNull Path dataFolder) {
        Path file = dataFolder.resolve(CATEGORIES_FILE);
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(dataFolder);
                try (InputStream defaults = PlayerNotificationsAdapterModule.class
                        .getClassLoader()
                        .getResourceAsStream(CATEGORIES_FILE)) {
                    if (defaults == null) {
                        throw new IllegalStateException(
                                "pn-adapter jar is missing its bundled " + CATEGORIES_FILE);
                    }
                    Files.copy(defaults, file, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            return YamlConfiguration.loadConfiguration(Files.newBufferedReader(file));
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to read " + CATEGORIES_FILE, ex);
        }
    }

    /**
     * Builds the mapper from a loaded {@code categories.yml}.
     */
    static @NotNull NotificationCategoryMapper readMapper(@NotNull YamlConfiguration config) {
        Objects.requireNonNull(config, "config");
        return new NotificationCategoryMapper(
                readStrings(config.getConfigurationSection("categories")),
                readStrings(config.getConfigurationSection("titles")),
                readStrings(config.getConfigurationSection("title-overrides")),
                readInts(config.getConfigurationSection("priorities")));
    }

    private static @NotNull Map<String, String> readStrings(@Nullable ConfigurationSection section) {
        Map<String, String> values = new HashMap<>();
        if (section != null) {
            for (String key : section.getKeys(false)) {
                String value = section.getString(key);
                if (value != null && !value.isBlank()) {
                    values.put(key, value);
                }
            }
        }
        return values;
    }

    private static @NotNull Map<String, Integer> readInts(@Nullable ConfigurationSection section) {
        Map<String, Integer> values = new HashMap<>();
        if (section != null) {
            for (String key : section.getKeys(false)) {
                values.put(key, section.getInt(key, 0));
            }
        }
        return values;
    }
}
