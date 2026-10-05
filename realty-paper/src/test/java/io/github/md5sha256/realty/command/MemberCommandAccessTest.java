package io.github.md5sha256.realty.command;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers {@link AddCommand#mayEditMembers(ProtectedRegion, LocalPlayer, boolean)}, shared by
 * {@link AddCommand} and {@link RemoveCommand} to decide who may edit a region's member list.
 */
class MemberCommandAccessTest {

    private static ProtectedRegion region() {
        return new ProtectedCuboidRegion("plot", BlockVector3.at(0, 0, 0), BlockVector3.at(1, 1, 1));
    }

    private static LocalPlayer player(UUID uuid) {
        LocalPlayer player = Mockito.mock(LocalPlayer.class);
        Mockito.when(player.getUniqueId()).thenReturn(uuid);
        return player;
    }

    @Test
    void groupOwner_mayEdit() {
        ProtectedRegion region = region();
        region.getOwners().addGroup("police");
        LocalPlayer player = player(UUID.randomUUID());
        Mockito.when(player.hasGroup("police")).thenReturn(true);

        assertTrue(AddCommand.mayEditMembers(region, player, false));
    }

    @Test
    void uuidOwner_mayEdit() {
        ProtectedRegion region = region();
        UUID uuid = UUID.randomUUID();
        region.getOwners().addPlayer(uuid);
        LocalPlayer player = player(uuid);

        assertTrue(AddCommand.mayEditMembers(region, player, false));
    }

    @Test
    void stranger_mayNotEdit() {
        ProtectedRegion region = region();
        LocalPlayer player = player(UUID.randomUUID());

        assertFalse(AddCommand.mayEditMembers(region, player, false));
    }

    @Test
    void othersPermission_mayEdit() {
        ProtectedRegion region = region();
        LocalPlayer player = player(UUID.randomUUID());

        assertTrue(AddCommand.mayEditMembers(region, player, true));
    }

}
