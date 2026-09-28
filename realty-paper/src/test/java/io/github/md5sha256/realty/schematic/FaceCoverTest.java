package io.github.md5sha256.realty.schematic;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class FaceCoverTest {

    @Test
    void mostBlocksCoverNothing() {
        Assertions.assertEquals(Faces.NONE, FaceCover.of("minecraft:glass"));
        Assertions.assertEquals(Faces.NONE, FaceCover.of("minecraft:torch"));
        Assertions.assertEquals(Faces.NONE, FaceCover.of("minecraft:oak_fence[east=true,north=false]"));
        Assertions.assertEquals(Faces.NONE, FaceCover.of("minecraft:oak_leaves[distance=1,persistent=true]"));
        Assertions.assertEquals(Faces.NONE,
                FaceCover.of("minecraft:chest[facing=north,type=single,waterlogged=false]"));
    }

    @Test
    void aSlabCoversTheFaceItLiesAgainst() {
        Assertions.assertEquals(Faces.DOWN, FaceCover.of("minecraft:oak_slab[type=bottom,waterlogged=false]"));
        Assertions.assertEquals(Faces.UP, FaceCover.of("minecraft:oak_slab[type=top,waterlogged=false]"));
        Assertions.assertEquals(Faces.ALL, FaceCover.of("minecraft:oak_slab[type=double,waterlogged=false]"));
    }

    @Test
    void aStairCoversItsTreadAndItsBack() {
        Assertions.assertEquals(Faces.DOWN | Faces.EAST,
                FaceCover.of("minecraft:oak_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]"));
        Assertions.assertEquals(Faces.UP | Faces.NORTH,
                FaceCover.of("minecraft:oak_stairs[facing=north,half=top,shape=straight,waterlogged=false]"));
    }

    @Test
    void aStairTurnedAtACornerIsCountedForItsTreadOnly() {
        Assertions.assertEquals(Faces.DOWN,
                FaceCover.of("minecraft:oak_stairs[facing=east,half=bottom,shape=outer_left,waterlogged=false]"));
    }

    @Test
    void carpetAndShallowSnowCoverWhatTheyLieOn() {
        Assertions.assertEquals(Faces.DOWN, FaceCover.of("minecraft:white_carpet"));
        Assertions.assertEquals(Faces.DOWN, FaceCover.of("minecraft:moss_carpet"));
        Assertions.assertEquals(Faces.DOWN, FaceCover.of("minecraft:snow[layers=3]"));
    }

    @Test
    void blocksASixteenthShortOfFullAreCountedAsFull() {
        Assertions.assertEquals(Faces.ALL, FaceCover.of("minecraft:snow[layers=8]"));
        Assertions.assertEquals(Faces.ALL, FaceCover.of("minecraft:farmland[moisture=7]"));
        Assertions.assertEquals(Faces.ALL, FaceCover.of("minecraft:dirt_path"));
    }

    @Test
    void aShutTrapdoorCoversTheFaceItLiesAgainst() {
        Assertions.assertEquals(Faces.DOWN,
                FaceCover.of("minecraft:oak_trapdoor[facing=north,half=bottom,open=false,powered=false]"));
        Assertions.assertEquals(Faces.UP,
                FaceCover.of("minecraft:oak_trapdoor[facing=north,half=top,open=false,powered=false]"));
    }

    @Test
    void anOpenTrapdoorStandsAgainstTheSideBehindIt() {
        Assertions.assertEquals(Faces.SOUTH,
                FaceCover.of("minecraft:oak_trapdoor[facing=north,half=bottom,open=true,powered=false]"));
        Assertions.assertEquals(Faces.EAST,
                FaceCover.of("minecraft:oak_trapdoor[facing=west,half=top,open=true,powered=false]"));
    }

    @Test
    void aShutDoorStandsOnTheEdgeBehindTheWayItFaces() {
        Assertions.assertEquals(Faces.WEST,
                FaceCover.of("minecraft:oak_door[facing=east,half=lower,hinge=left,open=false,powered=false]"));
        Assertions.assertEquals(Faces.SOUTH,
                FaceCover.of("minecraft:oak_door[facing=north,half=upper,hinge=right,open=false,powered=false]"));
    }

    @Test
    void anOpenDoorHasSwungAQuarterTurnAboutItsHinge() {
        // The vanilla shapes: facing east and open, a right-hinged door stands on the
        // south edge of its cell and a left-hinged one on the north.
        Assertions.assertEquals(Faces.SOUTH,
                FaceCover.of("minecraft:oak_door[facing=east,half=lower,hinge=right,open=true,powered=false]"));
        Assertions.assertEquals(Faces.NORTH,
                FaceCover.of("minecraft:oak_door[facing=east,half=lower,hinge=left,open=true,powered=false]"));
        Assertions.assertEquals(Faces.WEST,
                FaceCover.of("minecraft:oak_door[facing=south,half=lower,hinge=right,open=true,powered=false]"));
        Assertions.assertEquals(Faces.EAST,
                FaceCover.of("minecraft:oak_door[facing=north,half=lower,hinge=right,open=true,powered=false]"));
    }
}
