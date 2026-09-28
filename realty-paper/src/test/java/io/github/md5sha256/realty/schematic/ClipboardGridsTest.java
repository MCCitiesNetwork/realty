package io.github.md5sha256.realty.schematic;

import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.util.concurrency.LazyReference;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockTypes;
import org.enginehub.linbus.tree.LinCompoundTag;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.util.List;
import java.util.function.Predicate;

class ClipboardGridsTest {

    /** Far from the origin, so a leaked world coordinate would be obvious. */
    private static final BlockVector3 MIN = BlockVector3.at(1200, 64, -3400);
    private static final BlockVector3 MAX = BlockVector3.at(1202, 65, -3399);

    private static final Predicate<BlockState> STONE_OCCLUDES =
            state -> state.getBlockType().id().equals("minecraft:stone");

    @BeforeAll
    static void bootWorldEdit() {
        WorldEditTestPlatform.ensureRegistered();
    }

    private static BlockArrayClipboard clipboard() {
        return new BlockArrayClipboard(new CuboidRegion(MIN, MAX));
    }

    private static List<String> states(BlockGrid grid) {
        return grid.palette().stream().map(BlockGrid.PaletteEntry::state).toList();
    }

    @Test
    void theGridIsTheSizeOfTheRegion() throws Exception {
        BlockGrid grid = ClipboardGrids.fromClipboard(clipboard(), STONE_OCCLUDES);

        Assertions.assertEquals(3, grid.width());
        Assertions.assertEquals(2, grid.height());
        Assertions.assertEquals(2, grid.length());
    }

    @Test
    void blocksLandRelativeToTheRegionsCornerNotTheWorld() throws Exception {
        BlockArrayClipboard clipboard = clipboard();
        clipboard.setBlock(MIN, BlockTypes.STONE.getDefaultState());
        clipboard.setBlock(MAX, BlockTypes.DIRT.getDefaultState());

        BlockGrid grid = ClipboardGrids.fromClipboard(clipboard, STONE_OCCLUDES);

        Assertions.assertEquals("minecraft:stone",
                grid.palette().get(grid.cells()[grid.index(0, 0, 0)]).state());
        Assertions.assertEquals("minecraft:dirt",
                grid.palette().get(grid.cells()[grid.index(2, 1, 1)]).state());
    }

    @Test
    void airIsAlwaysPaletteEntryZero() throws Exception {
        BlockArrayClipboard clipboard = clipboard();
        clipboard.setBlock(MIN, BlockTypes.STONE.getDefaultState());

        BlockGrid grid = ClipboardGrids.fromClipboard(clipboard, STONE_OCCLUDES);

        Assertions.assertEquals("minecraft:air", grid.palette().getFirst().state());
        Assertions.assertEquals(BlockGrid.AIR, grid.cells()[grid.index(1, 0, 0)]);
    }

    @Test
    void everyKindOfAirIsTheSameAir() throws Exception {
        // The cull asks one question of a cell: is it air. Cave air that kept its own
        // palette entry would read as a block and seal whatever it surrounded.
        BlockArrayClipboard clipboard = clipboard();
        clipboard.setBlock(MIN, BlockTypes.CAVE_AIR.getDefaultState());

        BlockGrid grid = ClipboardGrids.fromClipboard(clipboard, STONE_OCCLUDES);

        Assertions.assertEquals(List.of("minecraft:air"), states(grid));
    }

    @Test
    void whatABlockDoesToTheViewIsRecordedPerBlock() throws Exception {
        BlockArrayClipboard clipboard = clipboard();
        clipboard.setBlock(MIN, BlockTypes.STONE.getDefaultState());
        clipboard.setBlock(MAX, BlockTypes.DIRT.getDefaultState());

        BlockGrid grid = ClipboardGrids.fromClipboard(clipboard, STONE_OCCLUDES);

        Assertions.assertEquals(BlockGrid.Sight.SOLID,
                grid.palette().get(grid.cells()[grid.index(0, 0, 0)]).sight());
        Assertions.assertEquals(BlockGrid.Sight.CLEAR,
                grid.palette().get(grid.cells()[grid.index(2, 1, 1)]).sight());
    }

    @Test
    void waterIsSeenThroughAsAirIsWhateverTheRegistrySaysOfIt() throws Exception {
        // A pond should show its bed. Treated as any other see-through block, water
        // would show one block of itself and hide everything under that.
        BlockArrayClipboard clipboard = clipboard();
        clipboard.setBlock(MIN, BlockTypes.WATER.getDefaultState());

        BlockGrid grid = ClipboardGrids.fromClipboard(clipboard, state -> true);

        BlockGrid.PaletteEntry water = grid.palette().get(grid.cells()[grid.index(0, 0, 0)]);
        Assertions.assertTrue(water.state().startsWith("minecraft:water"));
        Assertions.assertEquals(BlockGrid.Sight.OPEN, water.sight());
    }

    @Test
    void aBlockEntityKeepsItsIdAndNothingElse() throws Exception {
        BlockArrayClipboard clipboard = clipboard();
        LinCompoundTag chest = LinCompoundTag.builder().putString("id", "minecraft:chest").build();
        clipboard.setBlock(MIN, BlockTypes.CHEST.getDefaultState()
                .toBaseBlock(LazyReference.computed(chest)));

        BlockGrid grid = ClipboardGrids.fromClipboard(clipboard, STONE_OCCLUDES);

        BlockGrid.PaletteEntry entry = grid.palette().get(grid.cells()[grid.index(0, 0, 0)]);
        Assertions.assertTrue(entry.state().startsWith("minecraft:chest"));
        Assertions.assertEquals("minecraft:chest", entry.blockEntityId());
    }

    @Test
    void statesAreReducedBeforeTheyReachThePalette() {
        // The stub platform gives its blocks no properties, so a real leaf block prints
        // as bare "minecraft:oak_leaves" and would pass this whether or not anything was
        // reduced. The state is mocked to print the way a server's does.
        BlockState leaves = Mockito.mock(BlockState.class);
        Mockito.when(leaves.getBlockType()).thenReturn(BlockTypes.OAK_LEAVES);
        Mockito.when(leaves.getAsString())
                .thenReturn("minecraft:oak_leaves[distance=1,persistent=true,waterlogged=false]");
        BaseBlock block = Mockito.mock(BaseBlock.class);
        Mockito.when(block.toImmutableState()).thenReturn(leaves);
        Clipboard clipboard = Mockito.mock(Clipboard.class);
        Mockito.when(clipboard.getRegion()).thenReturn(new CuboidRegion(MIN, MIN));
        Mockito.when(clipboard.getFullBlock(ArgumentMatchers.any(BlockVector3.class))).thenReturn(block);

        BlockGrid grid = ClipboardGrids.fromClipboard(clipboard, STONE_OCCLUDES);

        Assertions.assertEquals(
                List.of("minecraft:air", "minecraft:oak_leaves[waterlogged=false]"), states(grid));
    }

    @Test
    void manyCellsOfOneBlockShareOnePaletteEntry() throws Exception {
        BlockArrayClipboard clipboard = clipboard();
        for (BlockVector3 position : clipboard.getRegion()) {
            clipboard.setBlock(position, BlockTypes.STONE.getDefaultState());
        }

        BlockGrid grid = ClipboardGrids.fromClipboard(clipboard, STONE_OCCLUDES);

        Assertions.assertEquals(List.of("minecraft:air", "minecraft:stone"), states(grid));
    }
}
