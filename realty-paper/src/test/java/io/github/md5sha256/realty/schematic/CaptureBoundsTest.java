package io.github.md5sha256.realty.schematic;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class CaptureBoundsTest {

    private static final BlockVector3 MIN = BlockVector3.at(10, -64, 10);
    private static final BlockVector3 MAX = BlockVector3.at(20, 319, 20);

    @Test
    void raisesTheFloorToWhereThePlayerStands() {
        CuboidRegion region = CaptureBounds.fromFloor(null, MIN, MAX, 64);
        Assertions.assertEquals(BlockVector3.at(10, 64, 10), region.getMinimumPoint());
        Assertions.assertEquals(MAX, region.getMaximumPoint());
    }

    @Test
    void keepsTheFootprintAndCeiling() {
        CuboidRegion region = CaptureBounds.fromFloor(null, MIN, MAX, 64);
        Assertions.assertEquals(MAX.x(), region.getMaximumPoint().x());
        Assertions.assertEquals(MAX.z(), region.getMaximumPoint().z());
        Assertions.assertEquals(MAX.y(), region.getMaximumPoint().y());
    }

    @Test
    void aFloorAtOrBelowTheRegionsFloorChangesNothing() {
        Assertions.assertEquals(MIN, CaptureBounds.fromFloor(null, MIN, MAX, -64).getMinimumPoint());
        Assertions.assertEquals(MIN, CaptureBounds.fromFloor(null, MIN, MAX, -100).getMinimumPoint());
    }

    @Test
    void aFloorAboveTheRegionTakesTheWholeRegionRatherThanNothing() {
        // Run from a balcony over the plot: there is no slice to take from above.
        CuboidRegion region = CaptureBounds.fromFloor(null, MIN, MAX, 320);
        Assertions.assertEquals(MIN, region.getMinimumPoint());
        Assertions.assertEquals(MAX, region.getMaximumPoint());
    }

    @Test
    void aFloorAtTheCeilingLeavesOneLayer() {
        CuboidRegion region = CaptureBounds.fromFloor(null, MIN, MAX, 319);
        Assertions.assertEquals(319, region.getMinimumPoint().y());
        Assertions.assertEquals(1L, RegionVolume.of(region) / (11L * 11L));
    }
}
