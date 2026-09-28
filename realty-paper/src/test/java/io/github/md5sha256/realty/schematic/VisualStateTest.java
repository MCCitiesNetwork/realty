package io.github.md5sha256.realty.schematic;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class VisualStateTest {

    @Test
    void aBlockWithNoPropertiesIsUnchanged() {
        Assertions.assertEquals("minecraft:stone", VisualState.reduce("minecraft:stone"));
    }

    @Test
    void propertiesThatShapeTheModelAreKept() {
        String stairs = "minecraft:oak_stairs[facing=north,half=bottom,shape=straight]";
        Assertions.assertEquals(stairs, VisualState.reduce(stairs));
    }

    @Test
    void leavesLoseWhatKeepsThemAlive() {
        // Pasted back, these leaves fall to the defaults and decay.
        Assertions.assertEquals("minecraft:oak_leaves",
                VisualState.reduce("minecraft:oak_leaves[distance=1,persistent=true]"));
    }

    @Test
    void droppingSomePropertiesKeepsTheRestInOrder() {
        Assertions.assertEquals("minecraft:scaffolding[bottom=true]",
                VisualState.reduce("minecraft:scaffolding[bottom=true,distance=3]"));
    }

    @Test
    void poweredIsDroppedOnlyWhereItChangesNothingVisible() {
        Assertions.assertEquals("minecraft:oak_door[facing=east,half=lower,hinge=left,open=false]",
                VisualState.reduce(
                        "minecraft:oak_door[facing=east,half=lower,hinge=left,open=false,powered=true]"));
        Assertions.assertEquals("minecraft:oak_trapdoor[facing=east,half=top,open=true]",
                VisualState.reduce("minecraft:oak_trapdoor[facing=east,half=top,open=true,powered=true]"));
        Assertions.assertEquals("minecraft:oak_fence_gate[facing=east,in_wall=false,open=false]",
                VisualState.reduce(
                        "minecraft:oak_fence_gate[facing=east,in_wall=false,open=false,powered=true]"));
    }

    @Test
    void poweredIsKeptWhereItIsTheModel() {
        // A lever, a button and a powered rail each draw differently when powered.
        String lever = "minecraft:lever[face=wall,facing=north,powered=true]";
        Assertions.assertEquals(lever, VisualState.reduce(lever));
        String rail = "minecraft:powered_rail[powered=true,shape=north_south]";
        Assertions.assertEquals(rail, VisualState.reduce(rail));
    }

    @Test
    void noteBlocksAreLeftAloneBecauseResourcePacksRetextureThem() {
        // Custom-block plugins map textures onto note block states, so dropping these
        // would draw the wrong block.
        String note = "minecraft:note_block[instrument=harp,note=3,powered=false]";
        Assertions.assertEquals(note, VisualState.reduce(note));
    }

    @Test
    void waterloggedIsKeptBecauseTheWaterIsDrawn() {
        String slab = "minecraft:stone_slab[type=bottom,waterlogged=true]";
        Assertions.assertEquals(slab, VisualState.reduce(slab));
    }

    @Test
    void triggeredIsDroppedOnDispensersButNotOnCrafters() {
        Assertions.assertEquals("minecraft:dispenser[facing=north]",
                VisualState.reduce("minecraft:dispenser[facing=north,triggered=true]"));
        String crafter = "minecraft:crafter[crafting=false,orientation=north_up,triggered=true]";
        Assertions.assertEquals(crafter, VisualState.reduce(crafter));
    }
}
