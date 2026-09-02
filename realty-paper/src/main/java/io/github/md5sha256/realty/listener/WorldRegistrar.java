package io.github.md5sha256.realty.listener;

import io.github.md5sha256.realty.database.Database;
import io.github.md5sha256.realty.database.SqlSessionWrapper;
import io.github.md5sha256.realty.database.mapper.RealtyWorldMapper;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Keeps the {@code RealtyWorld} table in step with the worlds Bukkit has loaded.
 *
 * <p>The REST API runs in a separate process and cannot ask Bukkit for a world's
 * name, so core projects the mapping into the database. Worlds are a bounded,
 * effectively immutable set and Bukkit fires load events for them, which is why
 * this projection can stay correct where one for WorldGuard geometry could not.</p>
 *
 * <p>Unloading a world does not delete its row. A region in an unloaded world is
 * still a region the API must be able to name.</p>
 */
public final class WorldRegistrar implements Listener {

    private final Database database;
    private final Executor databaseExecutor;

    public WorldRegistrar(@NotNull Database database, @NotNull Executor databaseExecutor) {
        this.database = database;
        this.databaseExecutor = databaseExecutor;
    }

    /**
     * Upserts every entry of {@code worlds}. Package-visible and static so it can be
     * tested without a running server.
     */
    static void syncAll(@NotNull RealtyWorldMapper mapper, @NotNull Map<UUID, String> worlds) {
        worlds.forEach(mapper::upsert);
    }

    /**
     * Reads the world list on the calling (main) thread, then writes off it.
     */
    public void syncLoadedWorlds(@NotNull Iterable<World> worlds) {
        Map<UUID, String> snapshot = new LinkedHashMap<>();
        for (World world : worlds) {
            snapshot.put(world.getUID(), world.getName());
        }
        write(snapshot);
    }

    @EventHandler
    public void onWorldLoad(@NotNull WorldLoadEvent event) {
        World world = event.getWorld();
        write(Map.of(world.getUID(), world.getName()));
    }

    private void write(@NotNull Map<UUID, String> snapshot) {
        if (snapshot.isEmpty()) {
            return;
        }
        this.databaseExecutor.execute(() -> {
            try (SqlSessionWrapper session = this.database.openSession(true)) {
                syncAll(session.realtyWorldMapper(), snapshot);
            }
        });
    }

}
