package io.github.md5sha256.realty.adapter.chat;

import com.minecraftcitiesnetwork.pluginInfrastructure.modules.SimplePluginModule;
import io.github.md5sha256.realty.Realty;
import net.kyori.adventure.audience.Audience;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.UUID;
import java.util.function.Function;

/**
 * Sends every Realty notification to its target's chat, when that target is online.
 */
public final class ChatAdapterModule extends SimplePluginModule<Realty> {

    @Override
    public void initialize(@NotNull Realty plugin, @NotNull Path dataFolder) {
        super.initialize(plugin, dataFolder);
        Function<UUID, Audience> lookup = Bukkit::getPlayer;
        registerListener(new ChatNotificationListener(
                plugin.executorState().mainThreadExec(), lookup));
    }

    @Override
    public void shutdown(@NotNull Realty plugin) {
        unregisterListeners();
        super.shutdown(plugin);
    }
}
