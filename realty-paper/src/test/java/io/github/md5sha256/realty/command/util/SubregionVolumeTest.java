package io.github.md5sha256.realty.command.util;

import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Polygonal2DRegion;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * The min-volume gate must measure the blocks WorldGuard will actually protect, not the polygon's
 * geometric area — a 4x5x1 footprint is 20 blocks however it was marked.
 */
class SubregionVolumeTest {

    private static final int Y = 64;

    @Test
    void cuboidCountsBlocks() {
        CuboidRegion region = new CuboidRegion(
                BlockVector3.at(0, Y, 0), BlockVector3.at(3, Y, 4));
        Assertions.assertEquals(20L, SubregionSelectionValidator.blockVolume(region));
    }

    @Test
    void polygonInPerimeterOrderCountsBlocksNotArea() {
        Polygonal2DRegion region = new Polygonal2DRegion(null, List.of(
                BlockVector2.at(0, 0), BlockVector2.at(3, 0),
                BlockVector2.at(3, 4), BlockVector2.at(0, 4)), Y, Y);
        Assertions.assertEquals(12L, region.getVolume(), "shoelace area, the buggy measure");
        Assertions.assertEquals(20L, SubregionSelectionValidator.blockVolume(region));
    }

    @Test
    void selfIntersectingPolygonCountsTheShapeItActuallyMakes() {
        // Clicking the four corners in reading order (TL, TR, BL, BR) walks a bow-tie, not the
        // rectangle the player meant. The shoelace area of a bow-tie cancels to exactly zero --
        // that is the "0 volume" report. The two triangles it really encloses are 12 blocks, so
        // this footprint is genuinely not the 4x5 the player wanted; the volume gate now says so
        // honestly instead of saying zero.
        Polygonal2DRegion region = new Polygonal2DRegion(null, List.of(
                BlockVector2.at(0, 0), BlockVector2.at(3, 0),
                BlockVector2.at(0, 4), BlockVector2.at(3, 4)), Y, Y);
        Assertions.assertEquals(0L, region.getVolume(), "shoelace area, the buggy measure");
        Assertions.assertEquals(12L, SubregionSelectionValidator.blockVolume(region));
    }
}
