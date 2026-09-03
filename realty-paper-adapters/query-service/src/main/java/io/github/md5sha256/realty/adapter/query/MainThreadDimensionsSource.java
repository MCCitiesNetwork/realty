package io.github.md5sha256.realty.adapter.query;

import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * Reads WorldGuard on the main thread. The measurement is a handful of O(1) field reads; the thread
 * hop is the cost. The caller applies the timeout, because it owns the request.
 *
 * <p>Both lookups are injected rather than reached for statically: the world lookup stands in for
 * {@code Server#getWorld}, and the region-manager lookup for WorldGuard's region container. That
 * keeps this class free of static service locators, so the read logic is unit-testable without a
 * running server.</p>
 */
public final class MainThreadDimensionsSource implements RegionDimensionsSource {

    private final Executor mainThread;
    private final Function<UUID, @Nullable World> worldLookup;
    private final Function<World, @Nullable RegionManager> regionManagerLookup;

    /**
     * @param mainThread          executor running tasks on the server main thread
     * @param worldLookup         world by unique id, {@code null} when no such world is loaded
     * @param regionManagerLookup WorldGuard region manager for a world, {@code null} when the world
     *                            is not region-managed
     */
    public MainThreadDimensionsSource(@NotNull Executor mainThread,
                                      @NotNull Function<UUID, @Nullable World> worldLookup,
                                      @NotNull Function<World, @Nullable RegionManager> regionManagerLookup) {
        this.mainThread = Objects.requireNonNull(mainThread, "mainThread");
        this.worldLookup = Objects.requireNonNull(worldLookup, "worldLookup");
        this.regionManagerLookup = Objects.requireNonNull(regionManagerLookup, "regionManagerLookup");
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

    private @NotNull Optional<RegionDimensions> readOnMainThread(@NotNull UUID worldId,
                                                                 @NotNull String regionId) {
        World world = this.worldLookup.apply(worldId);
        if (world == null) {
            return Optional.empty();
        }
        RegionManager manager = this.regionManagerLookup.apply(world);
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
