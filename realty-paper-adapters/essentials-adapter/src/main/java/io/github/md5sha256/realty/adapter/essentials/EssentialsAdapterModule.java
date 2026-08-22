package io.github.md5sha256.realty.adapter.essentials;

import com.earth2me.essentials.Console;
import com.earth2me.essentials.IEssentials;
import com.minecraftcitiesnetwork.pluginInfrastructure.modules.SimplePluginModule;
import io.github.md5sha256.realty.Realty;
import io.github.md5sha256.realty.command.util.SafeLocationFinder;
import net.ess3.api.IUser;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.UUID;

/**
 * Adds EssentialsX support to Realty: notifications for offline players become Essentials mail,
 * and teleport safety uses EssentialsX's own block checks.
 */
public final class EssentialsAdapterModule extends SimplePluginModule<Realty> {

    /**
     * {@inheritDoc}
     *
     * <p>{@code SimplePluginModule.initialize} does not declare a checked exception, so this
     * override cannot widen it back to {@code ModuleInitializationException} — an override's
     * throws clause may only narrow, never re-widen, what its superclass declares. The module
     * lifecycle manager that invokes modules through the {@code PluginModule} interface catches
     * {@code ModuleInitializationException | RuntimeException} identically (logs severe, unloads
     * the module), so an unchecked failure here is handled exactly the same way a checked one
     * would be.</p>
     *
     * @throws IllegalStateException if Essentials is absent or disabled
     */
    @Override
    public void initialize(@NotNull Realty plugin, @NotNull Path dataFolder) {
        super.initialize(plugin, dataFolder);
        Plugin essentialsPlugin = Bukkit.getPluginManager().getPlugin("Essentials");
        if (!(essentialsPlugin instanceof IEssentials essentials) || !essentialsPlugin.isEnabled()) {
            throw new IllegalStateException(
                    "EssentialsX is not installed or not enabled — essentials-adapter cannot start");
        }
        EssentialsAdapterConfig config = EssentialsAdapterConfig.read(dataFolder);
        // All fallible work must happen before the listener is registered: if anything after
        // registerListener throws, ModuleLifecycleManager closes the class loader without calling
        // shutdown(), so the listener would never be unregistered and would remain live on a dead
        // class loader.
        plugin.paperApi().setSafeBlockPredicate(new EssentialsSafeBlockPredicate(essentials));
        if (!config.notificationsEnabled()) {
            // Teleport safety above still applies — only mail delivery is switchable. Logged so an
            // operator wondering where their mail went is not left guessing.
            plugin.getLogger().info(
                    "essentials-adapter: notifications-enabled is false, so Realty notifications will "
                            + "not be delivered as EssentialsX mail. Teleport safety is unaffected.");
            return;
        }
        registerListener(new EssentialsMailListener(
                (uuid, text) -> sendMail(essentials, uuid, text),
                uuid -> Bukkit.getPlayer(uuid) != null,
                plugin.getLogger()));
    }

    @Override
    public void shutdown(@NotNull Realty plugin) {
        unregisterListeners();
        plugin.paperApi().setSafeBlockPredicate(SafeLocationFinder.defaultPredicate());
        super.shutdown(plugin);
    }

    private static void sendMail(@NotNull IEssentials essentials,
                                 @NotNull UUID target,
                                 @NotNull String text) {
        IUser user = essentials.getUser(target);
        if (user == null) {
            throw new IllegalStateException("no Essentials user for " + target);
        }
        essentials.getMail().sendMail(user, Console.getInstance(), text);
    }
}
