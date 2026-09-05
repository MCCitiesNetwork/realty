package io.github.md5sha256.realty.adapter.query;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.protection.flags.registry.SimpleFlagRegistry;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.managers.index.ChunkHashTable;
import com.sk89q.worldguard.protection.managers.index.PriorityRTreeIndex;
import com.sk89q.worldguard.protection.managers.storage.MemoryRegionDatabase;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.World;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * The three reads added for the v1.x module routes: a point/column lookup, WorldGuard's
 * owner and member domains, and the batch geometry read behind {@code /v1/worlds/geometry}.
 */
class MainThreadRegionSourceQueryTest {

    private static final UUID WORLD_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID ALICE = UUID.fromString("aaaa0000-0000-0000-0000-0000000000a1");
    private static final UUID BOB = UUID.fromString("bbbb0000-0000-0000-0000-0000000000b2");

    private static final Executor INLINE = Runnable::run;

    /** A ground-level plot and, stacked above it, a sky plot sharing the same footprint. */
    private static ProtectedCuboidRegion ground() {
        return new ProtectedCuboidRegion("ground",
                BlockVector3.at(0, 0, 0), BlockVector3.at(15, 63, 15));
    }

    private static ProtectedCuboidRegion sky() {
        return new ProtectedCuboidRegion("sky",
                BlockVector3.at(0, 128, 0), BlockVector3.at(15, 191, 15));
    }

    /**
     * A real WorldGuard manager, indexed exactly as a running server indexes one -- the chunk
     * table over a priority R-tree that {@code RegionContainerImpl} builds.
     *
     * <p>Real rather than mocked because what these tests are checking is which regions
     * WorldGuard's own index reports, and a mock would only report what the test told it to.</p>
     */
    private static RegionManager managerOf(ProtectedRegion... regions) {
        RegionManager manager = new RegionManager(new MemoryRegionDatabase(),
                new ChunkHashTable.Factory(new PriorityRTreeIndex.Factory()),
                new SimpleFlagRegistry());
        for (ProtectedRegion region : regions) {
            manager.addRegion(region);
        }
        return manager;
    }

    /** A world with real build limits, since a column test is asked as a shape spanning them. */
    private static World worldOfNormalHeight() {
        World world = Mockito.mock(World.class);
        Mockito.when(world.getMinHeight()).thenReturn(-64);
        Mockito.when(world.getMaxHeight()).thenReturn(320);
        return world;
    }

    private static MainThreadRegionSource sourceOver(RegionManager manager) {
        World world = worldOfNormalHeight();
        Function<UUID, World> worlds = id -> WORLD_ID.equals(id) ? world : null;
        return new MainThreadRegionSource(INLINE, worlds, w -> manager);
    }

    @Test
    void aColumnTestMatchesEveryRegionOverThatFootprintAtAnyHeight() {
        MainThreadRegionSource source = sourceOver(managerOf(ground(), sky()));
        // As a set: between two regions of equal priority the order carries no meaning, and
        // pinning one would only pin whichever way the index happened to hand them over.
        Assertions.assertEquals(Set.of("ground", "sky"),
                Set.copyOf(source.regionsAt(WORLD_ID, 8, null, 8).join().orElseThrow()));
    }

    @Test
    void theAnswerLeadsWithTheRegionWorldGuardWouldApply() {
        // Sorted the way the server itself resolves an overlap, so a caller asking what it is
        // standing in reads the region that governs the block first rather than whichever one
        // the index reached first.
        ProtectedCuboidRegion district = new ProtectedCuboidRegion("district",
                BlockVector3.at(0, 0, 0), BlockVector3.at(63, 255, 63));
        ProtectedCuboidRegion plot = new ProtectedCuboidRegion("plot",
                BlockVector3.at(0, 0, 0), BlockVector3.at(15, 255, 15));
        plot.setPriority(10);

        List<String> at = sourceOver(managerOf(district, plot))
                .regionsAt(WORLD_ID, 8, 30, 8).join().orElseThrow();

        Assertions.assertEquals(List.of("plot", "district"), at);
    }

    @Test
    void aLookupAsksTheIndexRatherThanReadingEveryRegionInTheWorld() {
        // The point of the exercise. Reading every region is what made this the one route
        // whose cost to the main thread grew with the size of the world.
        RegionManager manager = Mockito.spy(managerOf(ground(), sky()));
        MainThreadRegionSource source = sourceOver(manager);

        source.regionsAt(WORLD_ID, 8, 30, 8).join();
        source.regionsAt(WORLD_ID, 8, null, 8).join();

        Mockito.verify(manager, Mockito.never()).getRegions();
    }

    @Test
    void aPointTestExcludesARegionWhoseVerticalBandMissesTheBlock() {
        MainThreadRegionSource source = sourceOver(managerOf(ground(), sky()));
        Assertions.assertEquals(Optional.of(List.of("ground")),
                source.regionsAt(WORLD_ID, 8, 30, 8).join());
        Assertions.assertEquals(Optional.of(List.of("sky")),
                source.regionsAt(WORLD_ID, 8, 150, 8).join());
    }

    @Test
    void aBlockInsideNoRegionIsAnEmptyListNotAnAbsentWorld() {
        MainThreadRegionSource source = sourceOver(managerOf(ground()));
        Assertions.assertEquals(Optional.of(List.of()),
                source.regionsAt(WORLD_ID, 900, null, 900).join(),
                "the world exists and simply has nothing there; that is a 200, not a 404");
    }

    @Test
    void anUnknownWorldIsAbsentRatherThanEmpty() {
        MainThreadRegionSource source = new MainThreadRegionSource(
                INLINE, id -> null, w -> Mockito.mock(RegionManager.class));
        Assertions.assertEquals(Optional.empty(), source.regionsAt(WORLD_ID, 0, null, 0).join());
        Assertions.assertEquals(Optional.empty(), source.members(WORLD_ID, "ground").join());
    }

    @Test
    void readsBothWorldGuardDomains() {
        ProtectedCuboidRegion region = ground();
        region.getOwners().addPlayer(ALICE);
        region.getOwners().addGroup("staff");
        region.getMembers().addPlayer(BOB);
        region.getMembers().addPlayer("LegacyName");

        RegionMembers members = sourceOver(managerOf(region)).members(WORLD_ID, "ground").join()
                .orElseThrow();

        Assertions.assertEquals(List.of(ALICE.toString()), members.owners().playerIds());
        Assertions.assertEquals(List.of("staff"), members.owners().groups());
        Assertions.assertEquals(List.of(BOB.toString()), members.members().playerIds());
        Assertions.assertEquals(List.of("legacyname"), members.members().playerNames(),
                "WorldGuard lower-cases legacy name entries; report what it holds");
        Assertions.assertEquals(List.of(), members.members().groups());
    }

    @Test
    void anUnknownRegionHasNoMembers() {
        Assertions.assertEquals(Optional.empty(),
                sourceOver(managerOf(ground())).members(WORLD_ID, "nowhere").join());
    }

    @Test
    void batchGeometryAnswersOnlyTheRegionsThatExist() {
        Map<String, RegionDimensions> dims = sourceOver(managerOf(ground(), sky()))
                .dimensionsOf(WORLD_ID, List.of("ground", "nowhere", "sky")).join();

        Assertions.assertEquals(List.of("ground", "sky"), List.copyOf(dims.keySet()),
                "an unknown id is omitted rather than mapped to null");
        Assertions.assertEquals(0, dims.get("ground").minY());
        Assertions.assertEquals(191, dims.get("sky").maxY());
    }

    @Test
    void batchGeometryOverAnUnknownWorldIsEmpty() {
        MainThreadRegionSource source = new MainThreadRegionSource(
                INLINE, id -> null, w -> Mockito.mock(RegionManager.class));
        Assertions.assertEquals(Map.of(), source.dimensionsOf(WORLD_ID, List.of("ground")).join());
    }
}
