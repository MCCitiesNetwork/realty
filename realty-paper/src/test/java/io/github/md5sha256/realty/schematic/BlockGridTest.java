package io.github.md5sha256.realty.schematic;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class BlockGridTest {

    private static final List<BlockGrid.PaletteEntry> AIR_ONLY = List.of(BlockGrid.PaletteEntry.AIR);

    @Test
    void cellsRunXOutermostThenZThenYInnermost() {
        BlockGrid grid = new BlockGrid(3, 2, 2, AIR_ONLY, new int[12]);
        Assertions.assertEquals(0, grid.index(0, 0, 0));
        Assertions.assertEquals(1, grid.index(0, 1, 0));
        Assertions.assertEquals(2, grid.index(0, 0, 1));
        Assertions.assertEquals(4, grid.index(1, 0, 0));
        Assertions.assertEquals(11, grid.index(2, 1, 1));
    }

    @Test
    void refusesCellsThatDoNotMatchTheDimensions() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new BlockGrid(3, 2, 2, AIR_ONLY, new int[11]));
    }

    @Test
    void refusesAPaletteThatDoesNotStartWithAir() {
        List<BlockGrid.PaletteEntry> stoneFirst =
                List.of(new BlockGrid.PaletteEntry("minecraft:stone", "", BlockGrid.Sight.SOLID));
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new BlockGrid(1, 1, 1, stoneFirst, new int[1]));
    }

    @Test
    void refusesAnEmptyBox() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new BlockGrid(0, 1, 1, AIR_ONLY, new int[0]));
    }
}
