package io.github.md5sha256.realty.adapter.query;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionQuery.QueryOption;
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
 * <p>A batch is read a slice at a time rather than all at once. A map of a built-up world asks
 * about thousands of regions, and while each one is cheap, doing the lot inside a single task
 * spends the whole of one tick on it -- which the players on the server feel, and which gets
 * worse exactly when the server is busiest. Each slice hands the tick back and asks for the
 * next one, so a large batch costs a slice of several ticks instead of the whole of one.</p>
 *
 * <p>Both lookups are injected rather than reached for statically: the world lookup stands in for
 * {@code Server#getWorld}, and the region-manager lookup for WorldGuard's region container. That
 * keeps this class free of static service locators, so the read logic is unit-testable without a
 * running server.</p>
 */
public final class MainThreadRegionSource implements RegionSource {

    /**
     * How many regions one tick reads.
     *
     * <p>Sized against the tick rather than against the batch: a region is a map lookup and a
     * few field reads, so this is a fraction of a millisecond of a fifty-millisecond tick, and
     * the largest batch the module accepts is a couple of slices. It bounds the cost of a batch
     * whatever the batch limit is later set to, which is the point of it.</p>
     */
    public static final int DEFAULT_REGIONS_PER_TICK = 128;

    private final Executor mainThread;
    private final int regionsPerTick;
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
        this(mainThread, worldLookup, regionManagerLookup, DEFAULT_REGIONS_PER_TICK);
    }

    /** @param regionsPerTick how many regions a batch reads before handing the tick back */
    public MainThreadRegionSource(@NotNull Executor mainThread,
                                  @NotNull Function<UUID, @Nullable World> worldLookup,
                                  @NotNull Function<World, @Nullable RegionManager> regionManagerLookup,
                                  int regionsPerTick) {
        this.mainThread = Objects.requireNonNull(mainThread, "mainThread");
        this.worldLookup = Objects.requireNonNull(worldLookup, "worldLookup");
        this.regionManagerLookup = Objects.requireNonNull(regionManagerLookup, "regionManagerLookup");
        this.regionsPerTick = Math.max(1, regionsPerTick);
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
        CompletableFuture<Map<String, RegionDimensions>> answer = new CompletableFuture<>();
        if (requested.isEmpty()) {
            // Nothing to read is not worth a tick to discover.
            answer.complete(Map.of());
            return answer;
        }
        readSlice(worldId, requested, 0, new LinkedHashMap<>(), answer);
        return answer;
    }

    /**
     * Reads one tick's worth of the batch and asks for the next, until the batch is done.
     *
     * <p>Handing the work back to the executor is what spreads it: the main-thread executor
     * always runs a submitted task on a later tick, never inline, so each slice lands on a tick
     * of its own however deep the chain goes. The accumulating map is only ever touched from
     * the main thread, so it needs no synchronising of its own; completing the future carries
     * it safely across to the request thread waiting on it.</p>
     */
    private void readSlice(@NotNull UUID worldId,
                           @NotNull List<String> requested,
                           int from,
                           @NotNull Map<String, RegionDimensions> read,
                           @NotNull CompletableFuture<Map<String, RegionDimensions>> answer) {
        try {
            this.mainThread.execute(() -> {
                // The caller's timeout completes this future, so an abandoned request stops
                // costing ticks at the next slice boundary rather than reading to the end.
                if (answer.isDone()) {
                    return;
                }
                try {
                    RegionManager manager = manager(worldId);
                    if (manager == null) {
                        answer.complete(Map.of());
                        return;
                    }
                    int until = Math.min(from + this.regionsPerTick, requested.size());
                    for (int i = from; i < until; i++) {
                        String regionId = requested.get(i);
                        ProtectedRegion region = manager.getRegion(regionId);
                        if (region != null) {
                            read.put(regionId, RegionDimensions.fromProtectedRegion(region));
                        }
                    }
                    if (until == requested.size()) {
                        answer.complete(read);
                    } else {
                        readSlice(worldId, requested, until, read, answer);
                    }
                } catch (RuntimeException ex) {
                    answer.completeExceptionally(ex);
                }
            });
        } catch (RuntimeException ex) {
            // Bukkit's main-thread executor rejects tasks once the plugin is disabling; see
            // onMainThread below for why that becomes a failed future rather than a throw.
            answer.completeExceptionally(ex);
        }
    }

    /**
     * Which regions cover a block, asked of WorldGuard's own spatial index rather than of every
     * region in the world.
     *
     * <p>Both forms are indexed, which is the whole point of them being written this way. A
     * point goes through the chunk table, which holds the regions overlapping each 16 by 16
     * chunk and so compares a handful rather than thousands; where that chunk has not been
     * loaded it falls through to WorldGuard's priority R-tree. A column is an area query
     * against the same R-tree, using a one-block-wide probe spanning the world from floor to
     * build limit -- which is what "the footprint covers this x and z, at any height" means
     * once it has to be a shape rather than a two-dimensional test. Neither grows with the
     * size of the world the way reading every region did.</p>
     *
     * <p>{@link QueryOption#SORT} rather than the default: the default is
     * {@link QueryOption#COMPUTE_PARENTS}, which adds a matched region's parents to the answer
     * even where they do not cover the block, and this route is asked what covers a block.
     * Sorting is worth having on top, because it puts the answer in the order WorldGuard itself
     * applies the regions -- highest priority first -- which is the order a caller asking "what
     * am I standing in" wants to read.</p>
     *
     * <p>A region lying entirely outside the world's build range cannot be matched by the
     * column form. WorldGuard will not make one, and a block outside those bounds is not
     * somewhere a caller can be.</p>
     */
    @Override
    public @NotNull CompletableFuture<Optional<List<String>>> regionsAt(@NotNull UUID worldId,
                                                                        int x,
                                                                        @Nullable Integer y,
                                                                        int z) {
        return onMainThread(() -> {
            World world = this.worldLookup.apply(worldId);
            if (world == null) {
                return Optional.empty();
            }
            RegionManager manager = this.regionManagerLookup.apply(world);
            if (manager == null) {
                return Optional.empty();
            }
            ApplicableRegionSet applicable = y == null
                    ? manager.getApplicableRegions(columnAt(world, x, z), QueryOption.SORT)
                    : manager.getApplicableRegions(BlockVector3.at(x, y, z), QueryOption.SORT);
            List<String> matched = new ArrayList<>();
            for (ProtectedRegion region : applicable) {
                matched.add(region.getId());
            }
            return Optional.of(List.copyOf(matched));
        });
    }

    /**
     * A throwaway one-block column, the shape a column test has to be asked as.
     *
     * <p>Transient, so it is never a region WorldGuard could be asked to store or would report
     * to anyone; it exists only for the length of the query it is the argument to.</p>
     */
    private static @NotNull ProtectedRegion columnAt(@NotNull World world, int x, int z) {
        return new ProtectedCuboidRegion("realty_column_probe", true,
                BlockVector3.at(x, world.getMinHeight(), z),
                BlockVector3.at(x, world.getMaxHeight(), z));
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
