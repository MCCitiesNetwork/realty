package io.github.md5sha256.realty.command;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import io.github.md5sha256.realty.wand.SubregionWandManager;
import io.github.md5sha256.realty.wand.WandSelection;
import org.bukkit.World;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.UUID;

/** Where the height slider starts: the selection's own height, else the region being redefined. */
class SubregionRedefineHeightTest {

    private static final int MAX = 300;
    /** A region 10 blocks tall: Y 64 to 73. */
    private static final ProtectedCuboidRegion TARGET =
            new ProtectedCuboidRegion("plot", BlockVector3.at(0, 64, 0), BlockVector3.at(9, 73, 9));

    private static WandSelection footprint() {
        SubregionWandManager manager = new SubregionWandManager();
        UUID player = UUID.randomUUID();
        World world = Mockito.mock(World.class);
        manager.addPoint(player, world, BlockVector3.at(0, 64, 0));
        return manager.addPoint(player, world, BlockVector3.at(9, 64, 9));
    }

    @Test
    void withoutATargetTheDefaultIsUsed() {
        Assertions.assertEquals(16, SubregionDialog.initialHeight(footprint(), null, MAX));
    }

    @Test
    void aRedefineStartsAtTheTargetsHeight() {
        Assertions.assertEquals(10, SubregionDialog.initialHeight(footprint(), TARGET, MAX));
    }

    @Test
    void aHeightAlreadySetWinsOverTheTarget() {
        WandSelection selection = footprint();
        selection.setHeight(64, 68);
        Assertions.assertEquals(5, SubregionDialog.initialHeight(selection, TARGET, MAX));
    }

    @Test
    void theHeightIsClampedToTheRoomAboveTheFloor() {
        Assertions.assertEquals(4, SubregionDialog.initialHeight(footprint(), TARGET, 4));
    }
}
