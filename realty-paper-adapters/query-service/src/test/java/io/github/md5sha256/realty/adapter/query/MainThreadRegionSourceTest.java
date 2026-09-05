package io.github.md5sha256.realty.adapter.query;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import org.bukkit.World;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

class MainThreadRegionSourceTest {

    private static final UUID WORLD_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    /**
     * Runs inline, and counts how many tasks it was handed. Standing in for the main-thread
     * executor, it is the assertion that the WorldGuard read is dispatched rather than run on the
     * calling thread.
     */
    private static final class RecordingExecutor implements Executor {

        private final AtomicInteger tasks = new AtomicInteger();

        @Override
        public void execute(Runnable command) {
            this.tasks.incrementAndGet();
            command.run();
        }
    }

    /** Holds tasks until a test runs them, so a test can drive one tick at a time. */
    private static final class TickQueue implements Executor {

        private final java.util.Deque<Runnable> pending = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            this.pending.add(command);
        }

        void runOneTick() {
            Runnable task = this.pending.poll();
            if (task != null) {
                task.run();
            }
        }

        boolean hasPending() {
            return !this.pending.isEmpty();
        }
    }

    @Test
    void anUnknownWorldYieldsEmpty() {
        RecordingExecutor executor = new RecordingExecutor();
        MainThreadRegionSource source = new MainThreadRegionSource(
                executor, id -> null, world -> Mockito.mock(RegionManager.class));

        Assertions.assertEquals(Optional.empty(), source.dimensions(WORLD_ID, "plot").join());
        Assertions.assertEquals(1, executor.tasks.get());
    }

    @Test
    void aWorldWithoutARegionManagerYieldsEmpty() {
        RecordingExecutor executor = new RecordingExecutor();
        World world = Mockito.mock(World.class);
        MainThreadRegionSource source = new MainThreadRegionSource(
                executor, id -> world, w -> null);

        Assertions.assertEquals(Optional.empty(), source.dimensions(WORLD_ID, "plot").join());
        Assertions.assertEquals(1, executor.tasks.get());
    }

    @Test
    void anUnknownRegionYieldsEmpty() {
        RecordingExecutor executor = new RecordingExecutor();
        World world = Mockito.mock(World.class);
        RegionManager manager = Mockito.mock(RegionManager.class);
        Mockito.when(manager.getRegion("plot")).thenReturn(null);
        MainThreadRegionSource source = new MainThreadRegionSource(
                executor, id -> world, w -> manager);

        Assertions.assertEquals(Optional.empty(), source.dimensions(WORLD_ID, "plot").join());
        Assertions.assertEquals(1, executor.tasks.get());
    }

    @Test
    void aKnownRegionYieldsItsDimensions() {
        RecordingExecutor executor = new RecordingExecutor();
        World world = Mockito.mock(World.class);
        RegionManager manager = Mockito.mock(RegionManager.class);
        Mockito.when(manager.getRegion("plot")).thenReturn(new ProtectedCuboidRegion("plot",
                BlockVector3.at(104, 62, -88), BlockVector3.at(131, 140, -61)));
        Function<UUID, World> worldLookup = id -> WORLD_ID.equals(id) ? world : null;
        MainThreadRegionSource source = new MainThreadRegionSource(
                executor, worldLookup, w -> w == world ? manager : null);

        RegionDimensions dims = source.dimensions(WORLD_ID, "plot").join().orElseThrow();

        Assertions.assertEquals("CUBOID", dims.shape());
        Assertions.assertEquals(62, dims.minY());
        Assertions.assertEquals(140, dims.maxY());
        Assertions.assertEquals(List.of(
                new RegionDimensions.Point(104, -88),
                new RegionDimensions.Point(131, -88),
                new RegionDimensions.Point(131, -61),
                new RegionDimensions.Point(104, -61)), dims.points());
        Assertions.assertEquals(1, executor.tasks.get());
    }

    /** A manager holding one one-block cuboid per id, so a batch of any size can be asked for. */
    private static RegionManager managerHolding(List<String> ids) {
        RegionManager manager = Mockito.mock(RegionManager.class);
        for (String id : ids) {
            Mockito.when(manager.getRegion(id)).thenReturn(new ProtectedCuboidRegion(id,
                    BlockVector3.at(0, 0, 0), BlockVector3.at(1, 1, 1)));
        }
        return manager;
    }

    private static List<String> ids(int count) {
        List<String> ids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ids.add("plot_" + i);
        }
        return ids;
    }

    private static MainThreadRegionSource sourceOver(Executor executor, RegionManager manager,
                                                     int regionsPerTick) {
        World world = Mockito.mock(World.class);
        return new MainThreadRegionSource(executor, id -> WORLD_ID.equals(id) ? world : null,
                w -> w == world ? manager : null, regionsPerTick);
    }

    @Test
    void aBatchIsReadOverSeveralTicksRatherThanOne() {
        // A map of a built-up world asks about thousands of regions. Measuring the lot inside one
        // task spends a whole tick on it, which every player on the server feels.
        RecordingExecutor executor = new RecordingExecutor();
        List<String> requested = ids(250);
        MainThreadRegionSource source = sourceOver(executor, managerHolding(requested), 100);

        Map<String, RegionDimensions> dims = source.dimensionsOf(WORLD_ID, requested).join();

        Assertions.assertEquals(250, dims.size());
        // 100, 100, 50: the last slice finishes the batch rather than asking for another tick.
        Assertions.assertEquals(3, executor.tasks.get());
    }

    @Test
    void aBatchWithinOneTicksWorthIsStillOneTick() {
        RecordingExecutor executor = new RecordingExecutor();
        List<String> requested = ids(20);
        MainThreadRegionSource source = sourceOver(executor, managerHolding(requested), 100);

        Assertions.assertEquals(20, source.dimensionsOf(WORLD_ID, requested).join().size());
        Assertions.assertEquals(1, executor.tasks.get());
    }

    @Test
    void anEmptyBatchCostsNoTickAtAll() {
        // Nothing to read is not worth a hop onto the main thread to discover.
        RecordingExecutor executor = new RecordingExecutor();
        MainThreadRegionSource source = sourceOver(executor, managerHolding(List.of()), 100);

        Assertions.assertEquals(Map.of(), source.dimensionsOf(WORLD_ID, List.of()).join());
        Assertions.assertEquals(0, executor.tasks.get());
    }

    @Test
    void aBatchKeepsEveryRegionItPassedOverOnTheWay() {
        // The slices share one accumulating map; losing what an earlier slice read would leave a
        // map with holes in exactly the places a batch happened to be cut.
        RecordingExecutor executor = new RecordingExecutor();
        List<String> requested = ids(7);
        MainThreadRegionSource source = sourceOver(executor, managerHolding(requested), 2);

        Map<String, RegionDimensions> dims = new LinkedHashMap<>(
                source.dimensionsOf(WORLD_ID, requested).join());

        Assertions.assertEquals(requested, List.copyOf(dims.keySet()));
        Assertions.assertEquals(4, executor.tasks.get());
    }

    @Test
    void anAbandonedRequestStopsCostingTicks() {
        // The caller's timeout completes the future. Reading on to the end of a batch nobody is
        // waiting for is the main thread doing work for a page that has already gone.
        TickQueue ticks = new TickQueue();
        List<String> requested = ids(100);
        MainThreadRegionSource source = sourceOver(ticks, managerHolding(requested), 10);

        CompletableFuture<Map<String, RegionDimensions>> answer =
                source.dimensionsOf(WORLD_ID, requested);
        ticks.runOneTick();
        ticks.runOneTick();
        Assertions.assertTrue(ticks.hasPending());

        answer.completeExceptionally(new TimeoutException("gave up"));
        ticks.runOneTick();

        Assertions.assertFalse(ticks.hasPending());
    }
}
