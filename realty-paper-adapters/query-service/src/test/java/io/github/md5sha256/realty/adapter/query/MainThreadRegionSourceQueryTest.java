package io.github.md5sha256.realty.adapter.query;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.World;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    private static RegionManager managerOf(ProtectedRegion... regions) {
        Map<String, ProtectedRegion> byId = new LinkedHashMap<>();
        for (ProtectedRegion region : regions) {
            byId.put(region.getId(), region);
        }
        RegionManager manager = Mockito.mock(RegionManager.class);
        Mockito.when(manager.getRegions()).thenReturn(byId);
        for (ProtectedRegion region : regions) {
            Mockito.when(manager.getRegion(region.getId())).thenReturn(region);
        }
        return manager;
    }

    private static MainThreadRegionSource sourceOver(RegionManager manager) {
        World world = Mockito.mock(World.class);
        Function<UUID, World> worlds = id -> WORLD_ID.equals(id) ? world : null;
        return new MainThreadRegionSource(INLINE, worlds, w -> manager);
    }

    @Test
    void aColumnTestMatchesEveryRegionOverThatFootprintAtAnyHeight() {
        MainThreadRegionSource source = sourceOver(managerOf(ground(), sky()));
        Assertions.assertEquals(Optional.of(List.of("ground", "sky")),
                source.regionsAt(WORLD_ID, 8, null, 8).join());
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
