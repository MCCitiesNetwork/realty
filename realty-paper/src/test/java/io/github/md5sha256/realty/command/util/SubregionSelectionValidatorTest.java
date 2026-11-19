package io.github.md5sha256.realty.command.util;

import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.EllipsoidRegion;
import com.sk89q.worldedit.regions.Polygonal2DRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldguard.protection.flags.registry.SimpleFlagRegistry;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.managers.index.ChunkHashTable;
import com.sk89q.worldguard.protection.managers.index.PriorityRTreeIndex;
import com.sk89q.worldguard.protection.managers.storage.MemoryRegionDatabase;
import com.sk89q.worldguard.protection.regions.GlobalProtectedRegion;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedPolygonalRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

class SubregionSelectionValidatorTest {

    private RegionManager manager;
    private ProtectedCuboidRegion parent;
    private ProtectedCuboidRegion childA;
    private ProtectedCuboidRegion childB;

    @BeforeEach
    void setUp() throws ProtectedRegion.CircularInheritanceException {
        this.manager = new RegionManager(new MemoryRegionDatabase(),
                new ChunkHashTable.Factory(new PriorityRTreeIndex.Factory()), new SimpleFlagRegistry());
        this.parent = cuboid("parent", 0, 100);
        this.childA = cuboid("a", 10, 20);
        this.childB = cuboid("b", 80, 90);
        this.childA.setParent(this.parent);
        this.childB.setParent(this.parent);
        this.manager.addRegion(this.parent);
        this.manager.addRegion(this.childA);
        this.manager.addRegion(this.childB);
    }

    private static ProtectedCuboidRegion cuboid(String id, int from, int to) {
        return new ProtectedCuboidRegion(id, BlockVector3.at(from, from, from), BlockVector3.at(to, to, to));
    }

    private static Region selection(int from, int to) {
        return new CuboidRegion(BlockVector3.at(from, from, from), BlockVector3.at(to, to, to));
    }

    private static final List<BlockVector2> TRIANGLE =
            List.of(BlockVector2.at(0, 0), BlockVector2.at(10, 0), BlockVector2.at(5, 12));

    @Test
    void toProtectedRegionBuildsOnlyCuboidsAndPolygons() {
        ProtectedRegion cuboid = SubregionSelectionValidator.toProtectedRegion("plot", selection(0, 9)).orElseThrow();
        Assertions.assertInstanceOf(ProtectedCuboidRegion.class, cuboid);
        Assertions.assertEquals("plot", cuboid.getId());
        Assertions.assertEquals(BlockVector3.at(9, 9, 9), cuboid.getMaximumPoint());

        ProtectedPolygonalRegion polygon = Assertions.assertInstanceOf(ProtectedPolygonalRegion.class,
                SubregionSelectionValidator.toProtectedRegion("plot", new Polygonal2DRegion(null, TRIANGLE, 5, 15))
                        .orElseThrow());
        Assertions.assertEquals(TRIANGLE, polygon.getPoints());
        Assertions.assertEquals(5, polygon.getMinimumPoint().y());
        Assertions.assertEquals(15, polygon.getMaximumPoint().y());

        Assertions.assertEquals(Optional.empty(), SubregionSelectionValidator.toProtectedRegion("plot",
                new EllipsoidRegion(BlockVector3.at(0, 0, 0), Vector3.at(4, 4, 4))));
    }

    @Test
    void blockVolumeOfARegionCountsItsBlocks() {
        Assertions.assertEquals(11L * 11 * 11, SubregionSelectionValidator.blockVolume(this.childA));
        Assertions.assertEquals(0L, SubregionSelectionValidator.blockVolume(new GlobalProtectedRegion("global")));
    }

    @Test
    void aRegionIsNotItsOwnSibling() {
        Assertions.assertNull(SubregionSelectionValidator.overlappingSibling(
                selection(12, 18), this.parent, this.manager, "a"));
        Assertions.assertSame(this.childA, SubregionSelectionValidator.overlappingSibling(
                selection(12, 18), this.parent, this.manager, null));
    }

    @Test
    void aSiblingIsStillFoundWhenOneIsExcluded() {
        Assertions.assertSame(this.childB, SubregionSelectionValidator.overlappingSibling(
                selection(85, 88), this.parent, this.manager, "a"));
    }

    @Test
    void asSelectionReadsCuboidsAndPolygons() {
        Region cuboid = SubregionSelectionValidator.asSelection(this.childA).orElseThrow();
        Assertions.assertInstanceOf(CuboidRegion.class, cuboid);
        Assertions.assertEquals(this.childA.getMinimumPoint(), cuboid.getMinimumPoint());
        Assertions.assertEquals(this.childA.getMaximumPoint(), cuboid.getMaximumPoint());

        List<BlockVector2> points = List.of(BlockVector2.at(0, 0), BlockVector2.at(10, 0), BlockVector2.at(5, 12));
        Polygonal2DRegion polygon = Assertions.assertInstanceOf(Polygonal2DRegion.class,
                SubregionSelectionValidator.asSelection(new ProtectedPolygonalRegion("poly", points, 60, 80))
                        .orElseThrow());
        Assertions.assertEquals(points, polygon.getPoints());
        Assertions.assertEquals(60, polygon.getMinimumY());
        Assertions.assertEquals(80, polygon.getMaximumY());

        Assertions.assertEquals(Optional.empty(),
                SubregionSelectionValidator.asSelection(new GlobalProtectedRegion("global")));
    }

    @Test
    void firstChildOutsideFindsAChildLeftOutside() {
        Assertions.assertEquals(Optional.of(this.childB),
                SubregionSelectionValidator.firstChildOutside(this.parent, cuboid("parent", 0, 50), this.manager));
    }

    @Test
    void firstChildOutsideIsEmptyWhenAllFit() throws ProtectedRegion.CircularInheritanceException {
        ProtectedPolygonalRegion polygon = new ProtectedPolygonalRegion("poly",
                List.of(BlockVector2.at(40, 40), BlockVector2.at(50, 40), BlockVector2.at(45, 50)), 40, 50);
        polygon.setParent(this.parent);
        this.manager.addRegion(polygon);

        Assertions.assertEquals(Optional.empty(),
                SubregionSelectionValidator.firstChildOutside(this.parent, cuboid("parent", 0, 100), this.manager));
    }
}
