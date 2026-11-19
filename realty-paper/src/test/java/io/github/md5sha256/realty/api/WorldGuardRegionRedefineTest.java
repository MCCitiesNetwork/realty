package io.github.md5sha256.realty.api;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldguard.protection.flags.StringFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.managers.storage.MemoryRegionDatabase;
import com.sk89q.worldguard.protection.managers.storage.RegionDatabase;
import com.sk89q.worldguard.protection.managers.storage.StorageException;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedPolygonalRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import io.github.md5sha256.realty.command.util.SubregionSelectionValidator;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

/** Redefining a WorldGuard region: a region with the same id and the new shape replaces it. */
class WorldGuardRegionRedefineTest {

    private static final String ID = "plot";
    private static final StringFlag CUSTOM_FLAG = new StringFlag("realty-redefine-test-flag");
    private static final List<BlockVector2> TRIANGLE =
            List.of(BlockVector2.at(0, 0), BlockVector2.at(10, 0), BlockVector2.at(5, 12));

    private final World world = Mockito.mock(World.class);
    private final RegionContainer regionContainer = Mockito.mock(RegionContainer.class);
    private MockedStatic<BukkitAdapter> bukkitAdapter;
    private RegionManager manager;
    private WorldGuardRegionRedefine redefine;

    @BeforeEach
    void setUp() {
        this.bukkitAdapter = Mockito.mockStatic(BukkitAdapter.class);
        this.bukkitAdapter.when(() -> BukkitAdapter.adapt(Mockito.any(World.class))).thenReturn(null);
        useManager(new MemoryRegionDatabase());
        this.redefine = new WorldGuardRegionRedefine(this.regionContainer, Logger.getLogger("test"));
    }

    @AfterEach
    void tearDown() {
        this.bukkitAdapter.close();
    }

    private void useManager(RegionDatabase store) {
        this.manager = TestRegionManagers.storedIn(store);
        Mockito.when(this.regionContainer.get(Mockito.any())).thenReturn(this.manager);
    }

    private static ProtectedCuboidRegion cuboid(String id, int from, int to) {
        return new ProtectedCuboidRegion(id, BlockVector3.at(from, from, from), BlockVector3.at(to, to, to));
    }

    private static Region selection(int from, int to) {
        return new CuboidRegion(BlockVector3.at(from, from, from), BlockVector3.at(to, to, to));
    }

    private WorldGuardRegion added(ProtectedRegion region) {
        this.manager.addRegion(region);
        return new WorldGuardRegion(region, this.world);
    }

    private WorldGuardRegionRedefine.Outcome redefined(WorldGuardRegion region, ProtectedRegion replacement) {
        WorldGuardRegionRedefine.Outcome outcome = this.redefine.redefine(region, replacement);
        Assertions.assertEquals(Optional.empty(), outcome.saveError());
        return outcome;
    }

    /** A freehold 0..100 with a subregion {@code ID} at 10..20 in it. */
    private WorldGuardRegion subregionOfAFreehold() throws ProtectedRegion.CircularInheritanceException {
        ProtectedCuboidRegion parent = cuboid("parent", 0, 100);
        this.manager.addRegion(parent);
        ProtectedCuboidRegion plot = cuboid(ID, 10, 20);
        plot.setParent(parent);
        return added(plot);
    }

    private Optional<RealtyPaperApi.RedefineRegionResult> check(WorldGuardRegion region, Region selection,
                                                              boolean subregion, int minVolume) {
        ProtectedRegion replacement = SubregionSelectionValidator.toProtectedRegion(ID, selection).orElseThrow();
        return this.redefine.check(region, selection, replacement, subregion, minVolume);
    }

    @Test
    void aCuboidBecomesAPolygonAndKeepsItsSettings() throws ProtectedRegion.CircularInheritanceException {
        ProtectedCuboidRegion parent = cuboid("parent", -50, 50);
        this.manager.addRegion(parent);
        ProtectedCuboidRegion old = cuboid(ID, 0, 20);
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        old.getOwners().addPlayer(owner);
        old.getOwners().addGroup("landlords");
        old.getMembers().addPlayer(member);
        old.getMembers().addGroup("tenants");
        old.setFlag(CUSTOM_FLAG, "kept");
        old.setPriority(7);
        old.setParent(parent);
        ProtectedPolygonalRegion replacement = new ProtectedPolygonalRegion(ID, TRIANGLE, 0, 20);

        WorldGuardRegionRedefine.Outcome outcome = redefined(added(old), replacement);

        Assertions.assertSame(replacement, this.manager.getRegion(ID));
        Assertions.assertSame(replacement, outcome.region().region());
        Assertions.assertSame(this.world, outcome.region().world());
        Assertions.assertTrue(replacement.getOwners().contains(owner));
        Assertions.assertTrue(replacement.getOwners().getGroups().contains("landlords"));
        Assertions.assertTrue(replacement.getMembers().contains(member));
        Assertions.assertTrue(replacement.getMembers().getGroups().contains("tenants"));
        Assertions.assertEquals("kept", replacement.getFlag(CUSTOM_FLAG));
        Assertions.assertEquals(7, replacement.getPriority());
        Assertions.assertSame(parent, replacement.getParent());
        Assertions.assertEquals(TRIANGLE, replacement.getPoints());
    }

    @Test
    void aPolygonBecomesACuboid() {
        WorldGuardRegion region = added(new ProtectedPolygonalRegion(ID, TRIANGLE, 60, 80));
        ProtectedCuboidRegion replacement = cuboid(ID, 0, 30);

        redefined(region, replacement);

        ProtectedRegion now = Assertions.assertInstanceOf(ProtectedCuboidRegion.class, this.manager.getRegion(ID));
        Assertions.assertEquals(BlockVector3.at(0, 0, 0), now.getMinimumPoint());
        Assertions.assertEquals(BlockVector3.at(30, 30, 30), now.getMaximumPoint());
    }

    @Test
    void childrenPointAtTheReplacement() throws ProtectedRegion.CircularInheritanceException {
        ProtectedCuboidRegion old = cuboid(ID, 0, 100);
        ProtectedCuboidRegion child = cuboid("child", 10, 20);
        child.setParent(old);
        WorldGuardRegion region = added(old);
        this.manager.addRegion(child);
        ProtectedCuboidRegion replacement = cuboid(ID, 0, 150);

        redefined(region, replacement);

        Assertions.assertSame(replacement, child.getParent());
    }

    @Test
    void theSpatialIndexSeesTheNewShape() {
        WorldGuardRegion region = added(cuboid(ID, 0, 20));
        ProtectedCuboidRegion grown = cuboid(ID, 0, 40);

        redefined(region, grown);
        Assertions.assertTrue(this.manager.getApplicableRegions(BlockVector3.at(30, 30, 30)).getRegions()
                .contains(grown));

        redefined(new WorldGuardRegion(grown, this.world), cuboid(ID, 0, 10));
        Assertions.assertTrue(this.manager.getApplicableRegions(BlockVector3.at(15, 15, 15)).getRegions()
                .isEmpty());
    }

    @Test
    void aFailedSaveStillRedefinesAndReportsTheError() throws StorageException {
        RegionDatabase failing = Mockito.mock(RegionDatabase.class);
        Mockito.doThrow(new StorageException("disk full")).when(failing).saveAll(Mockito.any());
        Mockito.doThrow(new StorageException("disk full")).when(failing).saveChanges(Mockito.any());
        useManager(failing);
        ProtectedCuboidRegion replacement = cuboid(ID, 0, 30);

        WorldGuardRegionRedefine.Outcome outcome = this.redefine.redefine(added(cuboid(ID, 0, 20)), replacement);

        Assertions.assertEquals(Optional.of("disk full"), outcome.saveError());
        Assertions.assertSame(replacement, outcome.region().region());
        Assertions.assertSame(replacement, this.manager.getRegion(ID));
    }

    @Test
    void sameShapeComparesCornersAndPoints() {
        Assertions.assertTrue(WorldGuardRegionRedefine.sameShape(cuboid(ID, 0, 20), cuboid(ID, 0, 20)));
        Assertions.assertFalse(WorldGuardRegionRedefine.sameShape(cuboid(ID, 0, 20),
                new ProtectedCuboidRegion(ID, BlockVector3.at(0, 0, 0), BlockVector3.at(20, 21, 20))));
        Assertions.assertTrue(WorldGuardRegionRedefine.sameShape(new ProtectedPolygonalRegion(ID, TRIANGLE, 0, 9),
                new ProtectedPolygonalRegion(ID, TRIANGLE, 0, 9)));
        Assertions.assertFalse(WorldGuardRegionRedefine.sameShape(new ProtectedPolygonalRegion(ID, TRIANGLE, 0, 9),
                new ProtectedPolygonalRegion(ID, List.of(TRIANGLE.get(1), TRIANGLE.get(0), TRIANGLE.get(2)), 0, 9)));
        List<BlockVector2> square = List.of(BlockVector2.at(0, 0), BlockVector2.at(20, 0),
                BlockVector2.at(20, 20), BlockVector2.at(0, 20));
        Assertions.assertFalse(WorldGuardRegionRedefine.sameShape(cuboid(ID, 0, 20),
                new ProtectedPolygonalRegion(ID, square, 0, 20)));
    }

    @Test
    void checkRefusesASubregionOutsideItsParent() throws ProtectedRegion.CircularInheritanceException {
        Assertions.assertEquals(Optional.of(new RealtyPaperApi.RedefineRegionResult.OutsideParent("parent")),
                check(subregionOfAFreehold(), selection(90, 110), true, 1));
    }

    @Test
    void checkRefusesASubregionOverASibling() throws ProtectedRegion.CircularInheritanceException {
        WorldGuardRegion plot = subregionOfAFreehold();
        ProtectedCuboidRegion sibling = cuboid("sibling", 40, 50);
        sibling.setParent(plot.region().getParent());
        this.manager.addRegion(sibling);

        Assertions.assertEquals(Optional.of(new RealtyPaperApi.RedefineRegionResult.OverlapsSibling("sibling")),
                check(plot, selection(10, 45), true, 1));
    }

    @Test
    void checkRefusesASubregionBelowTheMinimum() throws ProtectedRegion.CircularInheritanceException {
        // 2 x 2 x 2 is 8 blocks: one under a minimum of 9, and exactly a minimum of 8.
        WorldGuardRegion plot = subregionOfAFreehold();
        Assertions.assertEquals(Optional.of(new RealtyPaperApi.RedefineRegionResult.TooSmall(8, 9)),
                check(plot, selection(10, 11), true, 9));
        Assertions.assertEquals(Optional.empty(), check(plot, selection(10, 11), true, 8));
    }

    @Test
    void checkRefusesAFreeholdLeavingAChildOutside() throws ProtectedRegion.CircularInheritanceException {
        ProtectedCuboidRegion freehold = cuboid(ID, 0, 100);
        WorldGuardRegion region = added(freehold);
        ProtectedCuboidRegion child = cuboid("child", 80, 90);
        child.setParent(freehold);
        this.manager.addRegion(child);

        Assertions.assertEquals(Optional.of(new RealtyPaperApi.RedefineRegionResult.ChildOutside("child")),
                check(region, selection(0, 50), false, 1));
    }

    @Test
    void checkPassesAValidShape() throws ProtectedRegion.CircularInheritanceException {
        Assertions.assertEquals(Optional.empty(), check(subregionOfAFreehold(), selection(10, 40), true, 20));
    }

    @Test
    void checkIgnoresSubregionRulesForATopLevelRegion() {
        WorldGuardRegion region = added(cuboid(ID, 0, 20));
        Assertions.assertEquals(Optional.empty(), check(region, selection(0, 1), false, 1000));
    }
}
