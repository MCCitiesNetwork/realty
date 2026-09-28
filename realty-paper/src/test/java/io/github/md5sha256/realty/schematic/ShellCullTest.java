package io.github.md5sha256.realty.schematic;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

class ShellCullTest {

    private static final int STONE = 1;
    private static final int CHEST = 2;
    private static final int SPAWNER = 3;
    private static final int GLASS = 4;
    private static final int STAIRS = 5;

    private static final List<BlockGrid.PaletteEntry> PALETTE = List.of(
            BlockGrid.PaletteEntry.AIR,
            new BlockGrid.PaletteEntry("minecraft:stone", "", true),
            new BlockGrid.PaletteEntry("minecraft:chest[facing=north,type=single]", "minecraft:chest", false),
            new BlockGrid.PaletteEntry("minecraft:spawner", "minecraft:mob_spawner", false),
            new BlockGrid.PaletteEntry("minecraft:glass", "", false),
            new BlockGrid.PaletteEntry("minecraft:oak_stairs[facing=east,half=bottom,shape=straight]", "", false));

    /** A sealed stone house with one-block walls, standing in open air. */
    private static BlockGrid house() {
        BlockGrid grid = new BlockGrid(7, 6, 7, PALETTE, new int[7 * 6 * 7]);
        for (int x = 1; x <= 5; x++) {
            for (int y = 0; y <= 4; y++) {
                for (int z = 1; z <= 5; z++) {
                    boolean wall = x == 1 || x == 5 || y == 0 || y == 4 || z == 1 || z == 5;
                    if (wall) {
                        grid.cells()[grid.index(x, y, z)] = STONE;
                    }
                }
            }
        }
        return grid;
    }

    private static void put(BlockGrid grid, int x, int y, int z, int block) {
        grid.cells()[grid.index(x, y, z)] = block;
    }

    private static String at(BlockGrid grid, int x, int y, int z) {
        return grid.palette().get(grid.cells()[grid.index(x, y, z)]).state();
    }

    private static List<String> states(BlockGrid grid) {
        return grid.palette().stream().map(BlockGrid.PaletteEntry::state).toList();
    }

    @Test
    void whatIsInsideASealedBuildingIsRemoved() {
        BlockGrid grid = house();
        put(grid, 3, 1, 3, CHEST);

        BlockGrid hollow = ShellCull.hollow(grid);

        Assertions.assertEquals("minecraft:air", at(hollow, 3, 1, 3));
    }

    @Test
    void theWallsAndRoofAreKept() {
        BlockGrid hollow = ShellCull.hollow(house());

        Assertions.assertEquals("minecraft:stone", at(hollow, 1, 2, 3));
        Assertions.assertEquals("minecraft:stone", at(hollow, 3, 4, 3));
        Assertions.assertEquals("minecraft:stone", at(hollow, 3, 0, 3));
    }

    @Test
    void aBlockTypeFoundOnlyInsideLeavesThePalette() {
        // The palette is served. Listing a spawner would say what the walls were hiding.
        BlockGrid grid = house();
        put(grid, 2, 1, 2, SPAWNER);

        BlockGrid hollow = ShellCull.hollow(grid);

        Assertions.assertFalse(states(hollow).contains("minecraft:spawner"));
        Assertions.assertEquals(List.of("minecraft:air", "minecraft:stone"), states(hollow));
    }

    @Test
    void aBlockUnderSomethingThatHidesNothingIsKept() {
        // The stair covers the roof block's only exposed face and occludes none of it.
        // Culling on touch alone left a hole under every stair, torch and flower.
        BlockGrid grid = house();
        put(grid, 3, 5, 3, STAIRS);

        BlockGrid hollow = ShellCull.hollow(grid);

        Assertions.assertEquals("minecraft:stone", at(hollow, 3, 4, 3));
        Assertions.assertTrue(at(hollow, 3, 5, 3).startsWith("minecraft:oak_stairs"));
    }

    @Test
    void glassShowsTheBlockBehindItAndNothingFurtherIn() {
        BlockGrid grid = house();
        put(grid, 1, 2, 3, GLASS);
        put(grid, 2, 2, 3, CHEST);
        put(grid, 4, 2, 3, CHEST);

        BlockGrid hollow = ShellCull.hollow(grid);

        Assertions.assertEquals("minecraft:glass", at(hollow, 1, 2, 3));
        Assertions.assertTrue(at(hollow, 2, 2, 3).startsWith("minecraft:chest"),
                "the block directly behind the window is visible through it");
        Assertions.assertEquals("minecraft:air", at(hollow, 4, 2, 3),
                "the room beyond is not");
    }

    @Test
    void anOpenDoorwayLetsTheViewIn() {
        BlockGrid grid = house();
        put(grid, 1, 1, 3, BlockGrid.AIR);
        put(grid, 1, 2, 3, BlockGrid.AIR);
        put(grid, 4, 1, 3, CHEST);

        BlockGrid hollow = ShellCull.hollow(grid);

        Assertions.assertTrue(at(hollow, 4, 1, 3).startsWith("minecraft:chest"));
    }

    @Test
    void aSolidMassKeepsOnlyItsSkin() {
        BlockGrid grid = new BlockGrid(5, 5, 5, PALETTE, new int[125]);
        Arrays.fill(grid.cells(), STONE);

        BlockGrid hollow = ShellCull.hollow(grid);

        Assertions.assertEquals("minecraft:air", at(hollow, 2, 2, 2));
        Assertions.assertEquals("minecraft:air", at(hollow, 1, 1, 1));
        Assertions.assertEquals("minecraft:stone", at(hollow, 0, 2, 2));
        Assertions.assertEquals("minecraft:stone", at(hollow, 2, 0, 2));
    }

    @Test
    void anEmptyBoxStaysEmpty() {
        BlockGrid hollow = ShellCull.hollow(new BlockGrid(2, 2, 2, PALETTE, new int[8]));

        Assertions.assertEquals(List.of("minecraft:air"), states(hollow));
        Assertions.assertArrayEquals(new int[8], hollow.cells());
    }

    @Test
    void theInputIsNotAltered() {
        BlockGrid grid = house();
        put(grid, 3, 1, 3, CHEST);
        int[] before = grid.cells().clone();

        ShellCull.hollow(grid);

        Assertions.assertArrayEquals(before, grid.cells());
    }
}
