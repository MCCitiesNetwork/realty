package io.github.md5sha256.realty.schematic;

import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.util.concurrency.LazyReference;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockTypes;
import org.enginehub.linbus.tree.LinCompoundTag;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.function.Predicate;

/**
 * The capture command makes one call to turn a clipboard into bytes. These tests make
 * the same call, so what they find missing from the bytes is missing from what is stored.
 */
class CaptureEncodingTest {

    private static final Predicate<BlockState> STONE_HIDES =
            state -> state.getBlockType().id().equals("minecraft:stone");

    @BeforeAll
    static void bootWorldEdit() {
        WorldEditTestPlatform.ensureRegistered();
    }

    /**
     * A sealed stone house, 5 by 5 by 5, with a chest inside, standing on a box that is
     * 7 by 6 by 7 and otherwise empty.
     */
    private static BlockArrayClipboard houseAt(BlockVector3 corner) throws Exception {
        BlockArrayClipboard clipboard =
                new BlockArrayClipboard(new CuboidRegion(corner, corner.add(6, 5, 6)));
        for (int x = 1; x <= 5; x++) {
            for (int y = 0; y <= 4; y++) {
                for (int z = 1; z <= 5; z++) {
                    boolean shell = x == 1 || x == 5 || y == 0 || y == 4 || z == 1 || z == 5;
                    if (shell) {
                        clipboard.setBlock(corner.add(x, y, z), BlockTypes.STONE.getDefaultState());
                    }
                }
            }
        }
        LinCompoundTag chest = LinCompoundTag.builder().putString("id", "minecraft:chest").build();
        clipboard.setBlock(corner.add(3, 1, 3),
                BlockTypes.CHEST.getDefaultState().toBaseBlock(LazyReference.computed(chest)));
        return clipboard;
    }

    @Test
    void whatIsStoredHoldsNothingFromInsideASealedBuilding() throws Exception {
        byte[] stored = CaptureEncoding.encode(houseAt(BlockVector3.at(100, 64, 100)), STONE_HIDES, 4325);

        RealtySchematicTestDecoder.Decoded decoded = RealtySchematicTestDecoder.decode(stored);

        Assertions.assertFalse(decoded.states().stream().anyMatch(state -> state.contains("chest")),
                "the chest inside is named in the palette: " + decoded.states());
        Assertions.assertFalse(decoded.blockEntityIds().contains("minecraft:chest"));
        Assertions.assertTrue(decoded.states().contains("minecraft:stone"), "the walls are still there");
    }

    @Test
    void theSameBuildSomewhereElseInTheWorldIsStoredAsTheSameBytes() throws Exception {
        // If where it stood reached the bytes in any form, these would differ.
        byte[] here = CaptureEncoding.encode(houseAt(BlockVector3.at(100, 64, 100)), STONE_HIDES, 4325);
        byte[] there = CaptureEncoding.encode(houseAt(BlockVector3.at(-52000, -32, 9731)), STONE_HIDES, 4325);

        Assertions.assertArrayEquals(here, there);
    }
}
