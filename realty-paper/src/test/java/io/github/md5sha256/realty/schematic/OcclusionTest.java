package io.github.md5sha256.realty.schematic;

import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.registry.BlockMaterial;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class OcclusionTest {

    /** A state as a server would describe it. The test platform describes almost nothing. */
    private static BlockState state(String id, String printed, boolean fullCube, boolean opaque) {
        BlockMaterial material = Mockito.mock(BlockMaterial.class);
        Mockito.when(material.isFullCube()).thenReturn(fullCube);
        Mockito.when(material.isOpaque()).thenReturn(opaque);
        BlockType type = Mockito.mock(BlockType.class);
        Mockito.when(type.id()).thenReturn(id);
        Mockito.when(type.getMaterial()).thenReturn(material);
        BlockState state = Mockito.mock(BlockState.class);
        Mockito.when(state.getBlockType()).thenReturn(type);
        Mockito.when(state.getAsString()).thenReturn(printed);
        return state;
    }

    @Test
    void aFullOpaqueCubeHides() {
        Assertions.assertTrue(Occlusion.hides(state("minecraft:stone", "minecraft:stone", true, true)));
    }

    @Test
    void glassIsAFullCubeAndHidesNothing() {
        Assertions.assertFalse(Occlusion.hides(state("minecraft:glass", "minecraft:glass", true, false)));
    }

    @Test
    void aStairIsOpaqueAndNotAFullCube() {
        Assertions.assertFalse(Occlusion.hides(state("minecraft:oak_stairs",
                "minecraft:oak_stairs[facing=north,half=bottom,shape=straight]", false, true)));
    }

    @Test
    void aHalfSlabHidesNothing() {
        Assertions.assertFalse(Occlusion.hides(state("minecraft:stone_slab",
                "minecraft:stone_slab[type=bottom,waterlogged=false]", false, true)));
    }

    @Test
    void aDoubleSlabHidesThoughItsTypeIsDescribedAsAHalf() {
        // WorldEdit describes the type by its default state, the bottom half. A wall of
        // double slabs read that way let the view through it.
        Assertions.assertTrue(Occlusion.hides(state("minecraft:stone_slab",
                "minecraft:stone_slab[type=double,waterlogged=false]", false, true)));
    }

    @Test
    void learningEveryMaterialLeavesEachTypeWithItsAnswer() {
        // The answer a type gives afterwards is the one it was given here, not a fresh
        // lookup: asked twice, it hands back the same object.
        WorldEditTestPlatform.ensureRegistered();

        Assertions.assertDoesNotThrow(Occlusion::learnEveryMaterial);

        for (BlockType type : BlockType.REGISTRY) {
            Assertions.assertSame(type.getMaterial(), type.getMaterial(), type.id());
        }
    }

    @Test
    void aBlockWithNoMaterialHidesNothing() {
        BlockType type = Mockito.mock(BlockType.class);
        Mockito.when(type.getMaterial()).thenReturn(null);
        BlockState state = Mockito.mock(BlockState.class);
        Mockito.when(state.getBlockType()).thenReturn(type);

        Assertions.assertFalse(Occlusion.hides(state));
    }
}
