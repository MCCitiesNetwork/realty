package io.github.md5sha256.realty.adapter.query;

import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedPolygonalRegion;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

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
}
