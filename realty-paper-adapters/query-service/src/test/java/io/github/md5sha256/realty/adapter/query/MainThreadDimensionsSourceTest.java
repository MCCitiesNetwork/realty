package io.github.md5sha256.realty.adapter.query;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import org.bukkit.World;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

class MainThreadDimensionsSourceTest {

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

    @Test
    void anUnknownWorldYieldsEmpty() {
        RecordingExecutor executor = new RecordingExecutor();
        MainThreadDimensionsSource source = new MainThreadDimensionsSource(
                executor, id -> null, world -> Mockito.mock(RegionManager.class));

        Assertions.assertEquals(Optional.empty(), source.dimensions(WORLD_ID, "plot").join());
        Assertions.assertEquals(1, executor.tasks.get());
    }

    @Test
    void aWorldWithoutARegionManagerYieldsEmpty() {
        RecordingExecutor executor = new RecordingExecutor();
        World world = Mockito.mock(World.class);
        MainThreadDimensionsSource source = new MainThreadDimensionsSource(
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
        MainThreadDimensionsSource source = new MainThreadDimensionsSource(
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
        MainThreadDimensionsSource source = new MainThreadDimensionsSource(
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
}
