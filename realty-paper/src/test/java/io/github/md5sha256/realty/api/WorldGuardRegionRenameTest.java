package io.github.md5sha256.realty.api;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.protection.flags.StringFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.managers.storage.MemoryRegionDatabase;
import com.sk89q.worldguard.protection.managers.storage.RegionDatabase;
import com.sk89q.worldguard.protection.managers.storage.StorageException;
import com.sk89q.worldguard.protection.regions.GlobalProtectedRegion;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedPolygonalRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/** Renaming a WorldGuard region: a copy under the new id replaces it, with everything it had. */
class WorldGuardRegionRenameTest {

    private static final String OLD_ID = "old-plot";
    private static final String NEW_ID = "new-plot";
    private static final StringFlag CUSTOM_FLAG = new StringFlag("realty-test-flag");

    private final World world = Mockito.mock(World.class);
    private final RegionContainer regionContainer = Mockito.mock(RegionContainer.class);
    private MockedStatic<BukkitAdapter> bukkitAdapter;
    private RegionManager manager;
    private WorldGuardRegionRename rename;

    @BeforeEach
    void setUp() {
        this.bukkitAdapter = Mockito.mockStatic(BukkitAdapter.class);
        this.bukkitAdapter.when(() -> BukkitAdapter.adapt(Mockito.any(World.class))).thenReturn(null);
        useManager(new MemoryRegionDatabase());
        this.rename = new WorldGuardRegionRename(this.regionContainer, Logger.getLogger("test"));
    }

    @AfterEach
    void tearDown() {
        this.bukkitAdapter.close();
    }

    private void useManager(RegionDatabase store) {
        this.manager = TestRegionManagers.storedIn(store);
        Mockito.when(this.regionContainer.get(Mockito.any())).thenReturn(this.manager);
    }

    private ProtectedCuboidRegion cuboid(String id) {
        return new ProtectedCuboidRegion(id, BlockVector3.at(0, 10, 0), BlockVector3.at(20, 40, 30));
    }

    private WorldGuardRegion added(ProtectedRegion region) {
        this.manager.addRegion(region);
        return new WorldGuardRegion(region, this.world);
    }

    private WorldGuardRegionRename.Outcome.Renamed renamed(WorldGuardRegion region) {
        return Assertions.assertInstanceOf(WorldGuardRegionRename.Outcome.Renamed.class,
                this.rename.rename(region, NEW_ID));
    }

    @Test
    void cuboidKeepsItsShapeAndSettings() throws ProtectedRegion.CircularInheritanceException {
        ProtectedCuboidRegion parent = new ProtectedCuboidRegion("parent",
                BlockVector3.at(-50, 0, -50), BlockVector3.at(50, 100, 50));
        this.manager.addRegion(parent);
        ProtectedCuboidRegion old = cuboid(OLD_ID);
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        old.getOwners().addPlayer(owner);
        old.getOwners().addGroup("landlords");
        old.getMembers().addPlayer(member);
        old.getMembers().addGroup("tenants");
        old.setFlag(CUSTOM_FLAG, "kept");
        old.setPriority(7);
        old.setParent(parent);

        WorldGuardRegionRename.Outcome.Renamed outcome = renamed(added(old));

        ProtectedRegion copy = this.manager.getRegion(NEW_ID);
        Assertions.assertNotNull(copy);
        Assertions.assertSame(copy, outcome.region().region());
        Assertions.assertSame(this.world, outcome.region().world());
        Assertions.assertNull(outcome.saveError());
        Assertions.assertInstanceOf(ProtectedCuboidRegion.class, copy);
        Assertions.assertEquals(old.getMinimumPoint(), copy.getMinimumPoint());
        Assertions.assertEquals(old.getMaximumPoint(), copy.getMaximumPoint());
        Assertions.assertTrue(copy.getOwners().contains(owner));
        Assertions.assertTrue(copy.getOwners().getGroups().contains("landlords"));
        Assertions.assertTrue(copy.getMembers().contains(member));
        Assertions.assertTrue(copy.getMembers().getGroups().contains("tenants"));
        Assertions.assertEquals(old.getFlags(), copy.getFlags());
        Assertions.assertEquals("kept", copy.getFlag(CUSTOM_FLAG));
        Assertions.assertEquals(7, copy.getPriority());
        Assertions.assertSame(parent, copy.getParent());
        Assertions.assertFalse(this.manager.hasRegion(OLD_ID));
    }

    @Test
    void polygonKeepsItsPointsAndHeight() {
        List<BlockVector2> points = List.of(BlockVector2.at(0, 0), BlockVector2.at(10, 0), BlockVector2.at(5, 12));
        ProtectedPolygonalRegion old = new ProtectedPolygonalRegion(OLD_ID, points, 60, 80);

        renamed(added(old));

        ProtectedPolygonalRegion copy =
                Assertions.assertInstanceOf(ProtectedPolygonalRegion.class, this.manager.getRegion(NEW_ID));
        Assertions.assertEquals(points, copy.getPoints());
        Assertions.assertEquals(60, copy.getMinimumPoint().y());
        Assertions.assertEquals(80, copy.getMaximumPoint().y());
    }

    @Test
    void childrenPointAtTheNewRegion() throws ProtectedRegion.CircularInheritanceException {
        ProtectedCuboidRegion old = cuboid(OLD_ID);
        ProtectedCuboidRegion child = new ProtectedCuboidRegion("child",
                BlockVector3.at(1, 11, 1), BlockVector3.at(5, 20, 5));
        child.setParent(old);
        WorldGuardRegion region = added(old);
        this.manager.addRegion(child);

        renamed(region);

        Assertions.assertTrue(this.manager.hasRegion("child"));
        Assertions.assertSame(this.manager.getRegion(NEW_ID), child.getParent());
    }

    @Test
    void takenIdChangesNothing() {
        ProtectedCuboidRegion old = cuboid(OLD_ID);
        WorldGuardRegion region = added(old);
        this.manager.addRegion(cuboid(NEW_ID));

        Assertions.assertInstanceOf(WorldGuardRegionRename.Outcome.NameTaken.class,
                this.rename.rename(region, NEW_ID));
        Assertions.assertSame(old, this.manager.getRegion(OLD_ID));
    }

    @Test
    void aFailedSaveStillRenamesAndReportsTheError() throws StorageException {
        RegionDatabase failing = Mockito.mock(RegionDatabase.class);
        Mockito.doThrow(new StorageException("disk full")).when(failing).saveAll(Mockito.any());
        Mockito.doThrow(new StorageException("disk full")).when(failing).saveChanges(Mockito.any());
        useManager(failing);

        WorldGuardRegionRename.Outcome.Renamed outcome = renamed(added(cuboid(OLD_ID)));

        Assertions.assertEquals("disk full", outcome.saveError());
        Assertions.assertTrue(this.manager.hasRegion(NEW_ID));
        Assertions.assertFalse(this.manager.hasRegion(OLD_ID));
    }

    @Test
    void isFreeReadsTheManager() {
        WorldGuardRegion region = added(cuboid(OLD_ID));

        Assertions.assertTrue(this.rename.isFree(region, NEW_ID));
        Assertions.assertFalse(this.rename.isFree(region, OLD_ID));
    }

    @Test
    void onlyCuboidsAndPolygonsAreSupported() {
        Assertions.assertTrue(WorldGuardRegionRename.isSupported(cuboid(OLD_ID)));
        Assertions.assertTrue(WorldGuardRegionRename.isSupported(new ProtectedPolygonalRegion(OLD_ID,
                List.of(BlockVector2.at(0, 0), BlockVector2.at(10, 0), BlockVector2.at(5, 12)), 0, 10)));
        Assertions.assertFalse(WorldGuardRegionRename.isSupported(new GlobalProtectedRegion("__global__")));
    }
}
