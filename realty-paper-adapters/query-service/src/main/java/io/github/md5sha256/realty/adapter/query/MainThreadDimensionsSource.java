package io.github.md5sha256.realty.adapter.query;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Reads WorldGuard on the main thread. The measurement is a handful of O(1) field reads; the thread
 * hop is the cost. The caller applies the timeout, because it owns the request.
 */
public final class MainThreadDimensionsSource implements RegionDimensionsSource {

    private final Executor mainThread;

    public MainThreadDimensionsSource(@NotNull Executor mainThread) {
        this.mainThread = Objects.requireNonNull(mainThread, "mainThread");
    }

    @Override
    public @NotNull CompletableFuture<Optional<RegionDimensions>> dimensions(@NotNull UUID worldId,
                                                                             @NotNull String regionId) {
        try {
            return CompletableFuture.supplyAsync(() -> readOnMainThread(worldId, regionId), this.mainThread);
        } catch (RuntimeException ex) {
            // Bukkit's main-thread executor rejects tasks once the plugin is disabling, throwing
            // synchronously (Paper's IllegalPluginAccessException, a RuntimeException). Returning a
            // failed future instead lets it surface through the handler's normal exception path as a
            // logged 500, rather than escaping out of a method that promises a future.
            return CompletableFuture.failedFuture(ex);
        }
    }

    private static @NotNull Optional<RegionDimensions> readOnMainThread(@NotNull UUID worldId,
                                                                         @NotNull String regionId) {
        World world = Bukkit.getWorld(worldId);
        if (world == null) {
            return Optional.empty();
        }
        RegionManager manager = WorldGuard.getInstance().getPlatform()
                .getRegionContainer().get(BukkitAdapter.adapt(world));
        if (manager == null) {
            return Optional.empty();
        }
        ProtectedRegion region = manager.getRegion(regionId);
        if (region == null) {
            return Optional.empty();
        }
        return Optional.of(RegionDimensions.fromProtectedRegion(region));
    }
}
