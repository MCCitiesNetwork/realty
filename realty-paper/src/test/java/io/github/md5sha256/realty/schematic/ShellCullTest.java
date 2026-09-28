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
    private static final int FENCE = 6;
    private static final int CARPET = 7;
    private static final int WATER = 8;
    private static final int DIAMOND = 9;
    private static final int SLAB = 10;
    private static final int TORCH = 11;
    private static final int TRAPDOOR = 12;
    private static final int DOOR = 13;

    private static final List<BlockGrid.PaletteEntry> PALETTE = List.of(
            BlockGrid.PaletteEntry.AIR,
            solid("minecraft:stone"),
            new BlockGrid.PaletteEntry("minecraft:chest[facing=north,type=single]", "minecraft:chest",
                    BlockGrid.Sight.CLEAR),
            new BlockGrid.PaletteEntry("minecraft:spawner", "minecraft:mob_spawner",
                    BlockGrid.Sight.CLEAR),
            seeThrough("minecraft:glass"),
            covering("minecraft:oak_stairs[facing=east,half=bottom,shape=straight]"),
            seeThrough("minecraft:oak_fence"),
            covering("minecraft:white_carpet"),
            new BlockGrid.PaletteEntry("minecraft:water[level=0]", "", BlockGrid.Sight.OPEN),
            solid("minecraft:diamond_block"),
            covering("minecraft:oak_slab[type=bottom]"),
            seeThrough("minecraft:torch"),
            // Set upright in a wall whose outside is to the west: hinged on its east side.
            covering("minecraft:spruce_trapdoor[facing=west,half=bottom,open=true]"),
            // Placed from outside, to the west of it, so it stands on its west edge.
            covering("minecraft:oak_door[facing=east,half=lower,hinge=left,open=false]"));

    private static BlockGrid.PaletteEntry solid(String state) {
        return new BlockGrid.PaletteEntry(state, "", BlockGrid.Sight.SOLID);
    }

    private static BlockGrid.PaletteEntry seeThrough(String state) {
        return new BlockGrid.PaletteEntry(state, "", BlockGrid.Sight.CLEAR);
    }

    /** Covers the faces its state says it covers, as a capture would have it. */
    private static BlockGrid.PaletteEntry covering(String state) {
        return new BlockGrid.PaletteEntry(state, "", BlockGrid.Sight.covering(FaceCover.of(state)));
    }

    /**
     * A sealed stone house standing in open air: walls, floor and roof one block thick,
     * from 1 to 5 on x and z, its floor on the bottom of the box at y 0 and its roof at
     * y 4. The rooms inside run from 2 to 4 on x and z, and from 1 to 3 on y.
     */
    private static BlockGrid house() {
        BlockGrid grid = new BlockGrid(7, 6, 7, PALETTE, new int[7 * 6 * 7]);
        for (int x = 1; x <= 5; x++) {
            for (int y = 0; y <= 4; y++) {
                for (int z = 1; z <= 5; z++) {
                    boolean shell = x == 1 || x == 5 || y == 0 || y == 4 || z == 1 || z == 5;
                    if (shell) {
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

    private static long blocksIn(BlockGrid grid) {
        return Arrays.stream(grid.cells()).filter(cell -> cell != BlockGrid.AIR).count();
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
        Assertions.assertEquals("minecraft:stone", at(hollow, 1, 0, 3), "a wall's foot, open to the side");
    }

    @Test
    void aBlockTypeFoundOnlyInsideLeavesThePalette() {
        // The palette is served. Listing a spawner would say what the walls were hiding.
        BlockGrid grid = house();
        put(grid, 2, 1, 2, SPAWNER);

        BlockGrid hollow = ShellCull.hollow(grid);

        Assertions.assertEquals(List.of("minecraft:air", "minecraft:stone"), states(hollow));
    }

    @Test
    void aBlockUnderSomethingThatHidesNothingIsKept() {
        // The torch stands on the roof block's only exposed face and hides none of it.
        // Culling on touch alone left a hole under every torch, fence and flower.
        BlockGrid grid = house();
        put(grid, 3, 5, 3, TORCH);

        BlockGrid hollow = ShellCull.hollow(grid);

        Assertions.assertEquals("minecraft:stone", at(hollow, 3, 4, 3));
        Assertions.assertEquals("minecraft:torch", at(hollow, 3, 5, 3));
    }

    @Test
    void aBlockUnderSomethingThatCoversItIsNot() {
        // A stair set on the roof has its whole tread on the block beneath. Nothing of
        // that block shows, and the stair drawn over the gap shows no gap.
        BlockGrid grid = house();
        put(grid, 3, 5, 3, STAIRS);

        BlockGrid hollow = ShellCull.hollow(grid);

        Assertions.assertTrue(at(hollow, 3, 5, 3).startsWith("minecraft:oak_stairs"));
        Assertions.assertEquals("minecraft:air", at(hollow, 3, 4, 3));
    }

    @Test
    void aSlabRoofDoesNotShowTheRoomUnderIt() {
        // Slabs are less than a full cube and nobody sees through one. Read as glass is
        // read, a slab roof published whatever stood on the top floor.
        BlockGrid grid = house();
        for (int x = 1; x <= 5; x++) {
            for (int z = 1; z <= 5; z++) {
                put(grid, x, 4, z, SLAB);
            }
        }
        put(grid, 3, 3, 3, CHEST);
        put(grid, 2, 3, 2, DIAMOND);
        put(grid, 4, 3, 4, SPAWNER);

        BlockGrid hollow = ShellCull.hollow(grid);

        Assertions.assertTrue(at(hollow, 3, 4, 3).startsWith("minecraft:oak_slab"), "the roof itself");
        Assertions.assertEquals("minecraft:air", at(hollow, 3, 3, 3));
        Assertions.assertEquals("minecraft:air", at(hollow, 2, 3, 2));
        Assertions.assertEquals(List.of("minecraft:air", "minecraft:stone", "minecraft:oak_slab[type=bottom]"),
                states(hollow));
    }

    @Test
    void aStairRoofDoesNotShowTheAtticUnderIt() {
        BlockGrid grid = house();
        for (int x = 1; x <= 5; x++) {
            for (int z = 1; z <= 5; z++) {
                put(grid, x, 4, z, STAIRS);
            }
        }
        put(grid, 3, 3, 3, CHEST);
        put(grid, 4, 3, 3, CHEST);

        BlockGrid hollow = ShellCull.hollow(grid);

        Assertions.assertFalse(states(hollow).stream().anyMatch(state -> state.contains("chest")),
                "an attic chest is named in the palette: " + states(hollow));
    }

    @Test
    void aPanelOfTrapdoorsInAWallHidesWhatIsBehindIt() {
        BlockGrid grid = house();
        for (int y = 1; y <= 3; y++) {
            for (int z = 2; z <= 4; z++) {
                put(grid, 1, y, z, TRAPDOOR);
                put(grid, 2, y, z, CHEST);
            }
        }

        BlockGrid hollow = ShellCull.hollow(grid);

        Assertions.assertTrue(at(hollow, 1, 2, 3).startsWith("minecraft:spruce_trapdoor"));
        Assertions.assertFalse(states(hollow).stream().anyMatch(state -> state.contains("chest")),
                "a chest behind the panel is named in the palette: " + states(hollow));
    }

    @Test
    void whatIsBuriedUnderACarpetStaysBuried() {
        // Open ground, a chest sunk into it, a carpet laid over the chest.
        BlockGrid grid = new BlockGrid(5, 3, 5, PALETTE, new int[75]);
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                put(grid, x, 0, z, STONE);
                put(grid, x, 1, z, STONE);
            }
        }
        put(grid, 2, 1, 2, CHEST);
        put(grid, 2, 2, 2, CARPET);

        BlockGrid hollow = ShellCull.hollow(grid);

        Assertions.assertEquals("minecraft:white_carpet", at(hollow, 2, 2, 2));
        Assertions.assertEquals("minecraft:air", at(hollow, 2, 1, 2));
    }

    @Test
    void aShutDoorHidesWhatStandsBehindIt() {
        BlockGrid grid = house();
        put(grid, 1, 1, 3, DOOR);
        put(grid, 2, 1, 3, CHEST);

        BlockGrid hollow = ShellCull.hollow(grid);

        Assertions.assertTrue(at(hollow, 1, 1, 3).startsWith("minecraft:oak_door"));
        Assertions.assertEquals("minecraft:air", at(hollow, 2, 1, 3));
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
        Assertions.assertEquals("minecraft:air", at(hollow, 4, 2, 3), "the room beyond is not");
    }

    @Test
    void theViewDoesNotRunOnThroughAChainOfBlocksThatHideNothing() {
        // A pane of glass in the roof, a fence post hanging from it, a spawner at the foot
        // of the post. Each hides nothing, and passing the view from one to the next
        // carried it from the roof to the floor of a sealed room.
        BlockGrid grid = house();
        put(grid, 3, 4, 3, GLASS);
        put(grid, 3, 3, 3, FENCE);
        put(grid, 3, 2, 3, FENCE);
        put(grid, 3, 1, 3, SPAWNER);
        put(grid, 2, 1, 3, DIAMOND);

        BlockGrid hollow = ShellCull.hollow(grid);

        Assertions.assertEquals("minecraft:oak_fence", at(hollow, 3, 3, 3), "directly behind the glass");
        Assertions.assertEquals("minecraft:air", at(hollow, 3, 2, 3));
        Assertions.assertEquals("minecraft:air", at(hollow, 3, 1, 3));
        Assertions.assertFalse(states(hollow).contains("minecraft:spawner"));
        Assertions.assertFalse(states(hollow).contains("minecraft:diamond_block"));
    }

    @Test
    void aCarpetedFloorDoesNotCarryTheViewAcrossTheRoom() {
        BlockGrid grid = house();
        put(grid, 1, 1, 3, GLASS);
        for (int x = 2; x <= 4; x++) {
            for (int z = 2; z <= 4; z++) {
                put(grid, x, 1, z, CARPET);
            }
        }
        put(grid, 4, 2, 4, SPAWNER);

        BlockGrid hollow = ShellCull.hollow(grid);

        Assertions.assertEquals("minecraft:white_carpet", at(hollow, 2, 1, 3), "directly behind the glass");
        Assertions.assertEquals("minecraft:air", at(hollow, 3, 1, 3));
        Assertions.assertEquals("minecraft:air", at(hollow, 4, 1, 4));
        Assertions.assertFalse(states(hollow).contains("minecraft:spawner"));
    }

    @Test
    void aFloodedRoomBehindAWindowStaysHidden() {
        // Water carries the view as air does, but only where the view reaches it in the
        // open. Behind glass it is the one block behind the glass and no more.
        BlockGrid grid = house();
        put(grid, 1, 2, 3, GLASS);
        for (int x = 2; x <= 4; x++) {
            for (int y = 1; y <= 3; y++) {
                for (int z = 2; z <= 4; z++) {
                    put(grid, x, y, z, WATER);
                }
            }
        }
        put(grid, 4, 3, 4, SPAWNER);

        BlockGrid hollow = ShellCull.hollow(grid);

        Assertions.assertTrue(at(hollow, 2, 2, 3).startsWith("minecraft:water"), "directly behind the glass");
        Assertions.assertEquals("minecraft:air", at(hollow, 3, 2, 3));
        Assertions.assertFalse(states(hollow).contains("minecraft:spawner"));
    }

    @Test
    void aFloorLevelWithTheGroundIsNotKeptForLyingOnTheBottomOfTheBox() {
        // The bottom of the box is where the capturing player stood, so a house level
        // with the ground has its whole floor there. Treating the bottom as seen kept
        // every block of it, and whatever was set into it.
        BlockGrid grid = house();
        put(grid, 3, 0, 3, DIAMOND);
        put(grid, 2, 0, 2, CHEST);

        BlockGrid hollow = ShellCull.hollow(grid);

        Assertions.assertEquals("minecraft:air", at(hollow, 3, 0, 3));
        Assertions.assertEquals("minecraft:air", at(hollow, 2, 0, 2));
        Assertions.assertEquals("minecraft:air", at(hollow, 4, 0, 4), "plain floor");
        Assertions.assertEquals(List.of("minecraft:air", "minecraft:stone"), states(hollow));
    }

    @Test
    void openGroundOnTheBottomOfTheBoxIsKept() {
        BlockGrid grid = house();
        put(grid, 0, 0, 0, STONE);
        put(grid, 6, 0, 3, STONE);

        BlockGrid hollow = ShellCull.hollow(grid);

        Assertions.assertEquals("minecraft:stone", at(hollow, 0, 0, 0));
        Assertions.assertEquals("minecraft:stone", at(hollow, 6, 0, 3));
    }

    @Test
    void aPondShowsItsBed() {
        // 5 wide, 5 long, stone all through, with a pond three deep cut into the top.
        BlockGrid grid = new BlockGrid(5, 5, 5, PALETTE, new int[125]);
        Arrays.fill(grid.cells(), STONE);
        for (int y = 2; y <= 4; y++) {
            put(grid, 2, y, 2, WATER);
        }

        BlockGrid hollow = ShellCull.hollow(grid);

        Assertions.assertTrue(at(hollow, 2, 2, 2).startsWith("minecraft:water"), "the deepest water");
        Assertions.assertEquals("minecraft:stone", at(hollow, 2, 1, 2), "the bed");
        Assertions.assertEquals("minecraft:stone", at(hollow, 1, 3, 2), "the bank, under water");
    }

    @Test
    void anOpenDoorwayLetsTheViewIn() {
        BlockGrid grid = house();
        put(grid, 1, 1, 3, BlockGrid.AIR);
        put(grid, 1, 2, 3, BlockGrid.AIR);
        put(grid, 4, 1, 3, CHEST);

        BlockGrid hollow = ShellCull.hollow(grid);

        Assertions.assertTrue(at(hollow, 4, 1, 3).startsWith("minecraft:chest"));
        Assertions.assertEquals("minecraft:stone", at(hollow, 3, 0, 3), "and the floor it now shows");
    }

    @Test
    void aRegionThatIsOnlyTheInsideOfARoomIsKeptWhole() {
        // Known, and not something the cull can mend. A region drawn inside a room's
        // walls has no outside: its edges are the view. The capture holds the room.
        BlockGrid grid = new BlockGrid(3, 3, 3, PALETTE, new int[27]);
        put(grid, 0, 0, 0, CHEST);
        put(grid, 1, 0, 1, SPAWNER);
        put(grid, 2, 1, 2, CHEST);

        BlockGrid hollow = ShellCull.hollow(grid);

        Assertions.assertEquals(3, blocksIn(hollow));
    }

    @Test
    void aSolidMassKeepsOnlyItsSidesAndTop() {
        BlockGrid grid = new BlockGrid(5, 5, 5, PALETTE, new int[125]);
        Arrays.fill(grid.cells(), STONE);

        BlockGrid hollow = ShellCull.hollow(grid);

        Assertions.assertEquals("minecraft:air", at(hollow, 2, 2, 2));
        Assertions.assertEquals("minecraft:air", at(hollow, 2, 0, 2), "the bottom is not a way in");
        Assertions.assertEquals("minecraft:stone", at(hollow, 0, 2, 2));
        Assertions.assertEquals("minecraft:stone", at(hollow, 2, 4, 2));
        Assertions.assertEquals("minecraft:stone", at(hollow, 0, 0, 2), "on a side, so seen");
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
