package io.github.md5sha256.realty.schematic;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class RegionVolumeTest {

    @Test
    void volumeCountsBothEndpoints() {
        CuboidRegion region = new CuboidRegion(BlockVector3.ZERO, BlockVector3.at(1, 1, 1));
        Assertions.assertEquals(8L, RegionVolume.of(region));
    }

    @Test
    void aSingleBlockRegionHasVolumeOne() {
        CuboidRegion region = new CuboidRegion(BlockVector3.ZERO, BlockVector3.ZERO);
        Assertions.assertEquals(1L, RegionVolume.of(region));
    }

    @Test
    void aRegionAtTheCapIsNotOverIt() {
        CuboidRegion region = new CuboidRegion(BlockVector3.ZERO, BlockVector3.at(1, 1, 1));
        Assertions.assertFalse(RegionVolume.exceedsCap(region, 8L));
    }

    @Test
    void aRegionOneBlockOverTheCapExceedsIt() {
        CuboidRegion region = new CuboidRegion(BlockVector3.ZERO, BlockVector3.at(1, 1, 1));
        Assertions.assertTrue(RegionVolume.exceedsCap(region, 7L));
    }

    @Test
    void aZeroOrNegativeCapDisablesTheCheck() {
        CuboidRegion region = new CuboidRegion(BlockVector3.ZERO, BlockVector3.at(99, 99, 99));
        Assertions.assertFalse(RegionVolume.exceedsCap(region, 0L));
        Assertions.assertFalse(RegionVolume.exceedsCap(region, -1L));
    }

    @Test
    void aLargeRegionDoesNotOverflowAnInt() {
        // 2000^3 is 8e9, well past Integer.MAX_VALUE -- the arithmetic must be long.
        CuboidRegion region = new CuboidRegion(BlockVector3.ZERO, BlockVector3.at(1999, 1999, 1999));
        Assertions.assertEquals(8_000_000_000L, RegionVolume.of(region));
    }
}
