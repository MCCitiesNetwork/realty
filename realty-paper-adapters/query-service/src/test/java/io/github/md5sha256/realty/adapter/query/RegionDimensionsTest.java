package io.github.md5sha256.realty.adapter.query;

import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedPolygonalRegion;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

class RegionDimensionsTest {

    @Test
    void aCuboidYieldsFourFootprintCorners() {
        ProtectedCuboidRegion cuboid = new ProtectedCuboidRegion("plot",
                BlockVector3.at(104, 62, -88), BlockVector3.at(131, 140, -61));

        RegionDimensions dims = RegionDimensions.fromProtectedRegion(cuboid);

        Assertions.assertEquals("CUBOID", dims.shape());
        Assertions.assertEquals(62, dims.minY());
        Assertions.assertEquals(140, dims.maxY());
        Assertions.assertEquals(List.of(
                new RegionDimensions.Point(104, -88),
                new RegionDimensions.Point(131, -88),
                new RegionDimensions.Point(131, -61),
                new RegionDimensions.Point(104, -61)), dims.points());
    }

    @Test
    void aPolygonKeepsItsPointsInOrder() {
        ProtectedPolygonalRegion polygon = new ProtectedPolygonalRegion("tri",
                List.of(BlockVector2.at(0, 0), BlockVector2.at(10, 0), BlockVector2.at(5, 8)),
                10, 20);

        RegionDimensions dims = RegionDimensions.fromProtectedRegion(polygon);

        Assertions.assertEquals("POLYGONAL", dims.shape());
        Assertions.assertEquals(10, dims.minY());
        Assertions.assertEquals(20, dims.maxY());
        Assertions.assertEquals(List.of(
                new RegionDimensions.Point(0, 0),
                new RegionDimensions.Point(10, 0),
                new RegionDimensions.Point(5, 8)), dims.points());
    }

    @Test
    void cornersOfAnUnorderedCuboidAreNormalised() {
        // WorldGuard normalises min/max itself; pin that we read those, not the constructor args.
        ProtectedCuboidRegion cuboid = new ProtectedCuboidRegion("plot",
                BlockVector3.at(131, 140, -61), BlockVector3.at(104, 62, -88));
        RegionDimensions dims = RegionDimensions.fromProtectedRegion(cuboid);
        Assertions.assertEquals(new RegionDimensions.Point(104, -88), dims.points().get(0));
        Assertions.assertEquals(62, dims.minY());
    }

    /**
     * A wall or path one block wide has {@code min.x() == max.x()}, so two of the four corners
     * coincide. Emitting them twice would draw a zero-length edge and skew any area computed from
     * the vertex list.
     */
    @Test
    void aCuboidOneBlockWideYieldsTwoPointsNotFour() {
        ProtectedCuboidRegion wall = new ProtectedCuboidRegion("wall",
                BlockVector3.at(10, 60, 20), BlockVector3.at(10, 70, 25));

        RegionDimensions dims = RegionDimensions.fromProtectedRegion(wall);

        Assertions.assertEquals(List.of(
                new RegionDimensions.Point(10, 20),
                new RegionDimensions.Point(10, 25)), dims.points());
        Assertions.assertEquals(60, dims.minY());
        Assertions.assertEquals(70, dims.maxY());
    }

    @Test
    void aOneByOneCuboidYieldsASinglePoint() {
        ProtectedCuboidRegion marker = new ProtectedCuboidRegion("marker",
                BlockVector3.at(10, 60, 20), BlockVector3.at(10, 70, 20));

        RegionDimensions dims = RegionDimensions.fromProtectedRegion(marker);

        Assertions.assertEquals(List.of(new RegionDimensions.Point(10, 20)), dims.points());
    }

    @Test
    void aPolygonWithARepeatedVertexKeepsOneOfEachInOrder() {
        ProtectedPolygonalRegion polygon = new ProtectedPolygonalRegion("tri",
                List.of(BlockVector2.at(0, 0), BlockVector2.at(10, 0),
                        BlockVector2.at(10, 0), BlockVector2.at(5, 8)),
                10, 20);

        RegionDimensions dims = RegionDimensions.fromProtectedRegion(polygon);

        Assertions.assertEquals(List.of(
                new RegionDimensions.Point(0, 0),
                new RegionDimensions.Point(10, 0),
                new RegionDimensions.Point(5, 8)), dims.points());
    }

    /** A polygon closed back onto its first vertex must not repeat that vertex. */
    @Test
    void aClosedPolygonDropsTheClosingVertex() {
        ProtectedPolygonalRegion polygon = new ProtectedPolygonalRegion("closed",
                List.of(BlockVector2.at(0, 0), BlockVector2.at(10, 0),
                        BlockVector2.at(5, 8), BlockVector2.at(0, 0)),
                10, 20);

        RegionDimensions dims = RegionDimensions.fromProtectedRegion(polygon);

        Assertions.assertEquals(List.of(
                new RegionDimensions.Point(0, 0),
                new RegionDimensions.Point(10, 0),
                new RegionDimensions.Point(5, 8)), dims.points());
    }

    @Test
    void aNormalCuboidStillYieldsFourCornersInOrder() {
        ProtectedCuboidRegion cuboid = new ProtectedCuboidRegion("plot",
                BlockVector3.at(104, 62, -88), BlockVector3.at(131, 140, -61));

        RegionDimensions dims = RegionDimensions.fromProtectedRegion(cuboid);

        Assertions.assertEquals(4, dims.points().size());
        Assertions.assertEquals(dims.points().size(),
                Set.copyOf(dims.points()).size(), "no repeated vertices");
    }

    @Test
    void aRegionCarriesTheWorldGuardPriorityThatSettlesItsOverlaps() {
        // Two regions covering one block are settled by priority, and a client drawing them has
        // the same problem: a plot inside a district must be drawn over the district, not under.
        ProtectedCuboidRegion plot = new ProtectedCuboidRegion("plot",
                BlockVector3.at(0, 0, 0), BlockVector3.at(15, 255, 15));
        plot.setPriority(7);

        Assertions.assertEquals(7, RegionDimensions.fromProtectedRegion(plot).priority());
    }

    @Test
    void aRegionNobodyHasPrioritisedReportsZero() {
        // WorldGuard's own default, so an unset priority and an absent one agree.
        ProtectedCuboidRegion plot = new ProtectedCuboidRegion("plot",
                BlockVector3.at(0, 0, 0), BlockVector3.at(15, 255, 15));

        Assertions.assertEquals(0, RegionDimensions.fromProtectedRegion(plot).priority());
    }
}
