package io.github.md5sha256.realty.adapter.query;

import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Reads WorldGuard on the main thread. Each measurement is a handful of O(1) field reads; the
 * thread hop is the cost. The caller applies the timeout, because it owns the request.
 *
 * <p>Both lookups are injected rather than reached for statically: the world lookup stands in for
 * {@code Server#getWorld}, and the region-manager lookup for WorldGuard's region container. That
 * keeps this class free of static service locators, so the read logic is unit-testable without a
 * running server.</p>
 */
public final class MainThreadRegionSource implements RegionSource {

    private final Executor mainThread;
    private final Function<UUID, @Nullable World> worldLookup;
    private final Function<World, @Nullable RegionManager> regionManagerLookup;

    /**
     * @param mainThread          executor running tasks on the server main thread
     * @param worldLookup         world by unique id, {@code null} when no such world is loaded
     * @param regionManagerLookup WorldGuard region manager for a world, {@code null} when the world
     *                            is not region-managed
     */
    public MainThreadRegionSource(@NotNull Executor mainThread,
                                  @NotNull Function<UUID, @Nullable World> worldLookup,
                                  @NotNull Function<World, @Nullable RegionManager> regionManagerLookup) {
        this.mainThread = Objects.requireNonNull(mainThread, "mainThread");
        this.worldLookup = Objects.requireNonNull(worldLookup, "worldLookup");
        this.regionManagerLookup = Objects.requireNonNull(regionManagerLookup, "regionManagerLookup");
    }

    @Override
    public @NotNull CompletableFuture<Optional<RegionDimensions>> dimensions(@NotNull UUID worldId,
                                                                             @NotNull String regionId) {
        return onMainThread(() -> {
            ProtectedRegion region = region(worldId, regionId);
            return region == null ? Optional.empty()
                    : Optional.of(RegionDimensions.fromProtectedRegion(region));
        });
    }

    @Override
    public @NotNull CompletableFuture<Map<String, RegionDimensions>> dimensionsOf(
            @NotNull UUID worldId, @NotNull Collection<String> regionIds) {
        // Copied before the hop so the caller cannot mutate the list out from under the main thread.
        List<String> requested = List.copyOf(new LinkedHashSet<>(regionIds));
        return onMainThread(() -> {
            RegionManager manager = manager(worldId);
            if (manager == null) {
                return Map.of();
            }
            Map<String, RegionDimensions> dims = new LinkedHashMap<>();
            for (String regionId : requested) {
                ProtectedRegion region = manager.getRegion(regionId);
                if (region != null) {
                    dims.put(regionId, RegionDimensions.fromProtectedRegion(region));
                }
            }
            return dims;
        });
    }

    @Override
    public @NotNull CompletableFuture<Optional<List<String>>> regionsAt(@NotNull UUID worldId,
                                                                        int x,
                                                                        @Nullable Integer y,
                                                                        int z) {
        return onMainThread(() -> {
            RegionManager manager = manager(worldId);
            if (manager == null) {
                return Optional.empty();
            }
            // A point test could use the manager's spatial index, but a column test has no
            // indexed form -- ApplicableRegionSet is asked about a BlockVector3. Walking the
            // region map keeps the two answers built the same way and in the same order, which
            // matters because the response tells the caller which test ran.
            BlockVector2 column = BlockVector2.at(x, z);
            List<String> matched = new ArrayList<>();
            for (ProtectedRegion region : manager.getRegions().values()) {
                boolean hit = y == null
                        ? region.contains(column)
                        : region.contains(BlockVector3.at(x, y, z));
                if (hit) {
                    matched.add(region.getId());
                }
            }
            return Optional.of(List.copyOf(matched));
        });
    }

    @Override
    public @NotNull CompletableFuture<Optional<RegionMembers>> members(@NotNull UUID worldId,
                                                                       @NotNull String regionId) {
        return onMainThread(() -> {
            ProtectedRegion region = region(worldId, regionId);
            return region == null ? Optional.empty()
                    : Optional.of(RegionMembers.fromProtectedRegion(region));
        });
    }

    /**
     * Bukkit's main-thread executor rejects tasks once the plugin is disabling, throwing
     * synchronously (Paper's {@code IllegalPluginAccessException}, a {@link RuntimeException}).
     * Returning a failed future instead lets it surface through the handler's normal exception
     * path as a logged 500, rather than escaping out of a method that promises a future.
     */
    private <T> @NotNull CompletableFuture<T> onMainThread(@NotNull Supplier<T> read) {
        try {
            return CompletableFuture.supplyAsync(read, this.mainThread);
        } catch (RuntimeException ex) {
            return CompletableFuture.failedFuture(ex);
        }
    }

    private @Nullable RegionManager manager(@NotNull UUID worldId) {
        World world = this.worldLookup.apply(worldId);
        if (world == null) {
            return null;
        }
        return this.regionManagerLookup.apply(world);
    }

    private @Nullable ProtectedRegion region(@NotNull UUID worldId, @NotNull String regionId) {
        RegionManager manager = manager(worldId);
        return manager == null ? null : manager.getRegion(regionId);
    }
}
