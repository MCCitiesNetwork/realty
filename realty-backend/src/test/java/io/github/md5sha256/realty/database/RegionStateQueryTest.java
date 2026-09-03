package io.github.md5sha256.realty.database;

import io.github.md5sha256.realty.database.entity.RegionStateRow;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The {@code state} projection behind the {@code /v1/regions} listing.
 *
 * <p>Every case here is a branch of the {@code CASE} that derives state from contract
 * nullity in SQL, mirroring what {@code RealtyBackendImpl#getRegionState} does in Java.
 * The two must agree, so the state names are asserted literally.</p>
 */
class RegionStateQueryTest extends AbstractDatabaseTest {

    private static final UUID WORLD_ID = UUID.fromString("8f4d1c2e-0000-0000-0000-000000000021");
    private static final UUID OTHER_WORLD = UUID.fromString("8f4d1c2e-0000-0000-0000-000000000022");
    private static final UUID AUTHORITY = UUID.fromString("3a1c88f0-0000-0000-0000-000000000030");
    private static final UUID OWNER = UUID.fromString("3a1c88f0-0000-0000-0000-000000000031");
    private static final UUID LANDLORD = UUID.fromString("3a1c88f0-0000-0000-0000-000000000032");
    private static final UUID TENANT = UUID.fromString("3a1c88f0-0000-0000-0000-000000000033");

    @BeforeEach
    void seed() {
        Assertions.assertTrue(logic.createFreehold("plot_for_sale", WORLD_ID, 5000.0, AUTHORITY, null));
        Assertions.assertTrue(logic.createFreehold("plot_sold", WORLD_ID, null, AUTHORITY, OWNER));
        Assertions.assertTrue(logic.createLeasehold("plot_for_lease", WORLD_ID, 250.0, 604800L, -1, LANDLORD));
        try (SqlSessionWrapper session = database.openSession(true)) {
            session.realtyRegionMapper().registerWorldGuardRegion("plot_bare", WORLD_ID);
        }
        Assertions.assertTrue(logic.createFreehold("plot_elsewhere", OTHER_WORLD, 100.0, AUTHORITY, null));
    }

    private static List<RegionStateRow> page(int limit, int offset) {
        try (SqlSessionWrapper session = database.openSession(true)) {
            return session.realtyRegionMapper().selectPageWithState(limit, offset);
        }
    }

    private static List<RegionStateRow> pageInWorld(UUID worldId, int limit, int offset) {
        try (SqlSessionWrapper session = database.openSession(true)) {
            return session.realtyRegionMapper().selectPageWithStateByWorld(worldId, limit, offset);
        }
    }

    private static String stateOf(List<RegionStateRow> rows, String regionId) {
        for (RegionStateRow row : rows) {
            if (row.worldGuardRegionId().equals(regionId)) {
                return row.state();
            }
        }
        return Assertions.fail("no row for " + regionId + " in " + ids(rows));
    }

    private static List<String> ids(List<RegionStateRow> rows) {
        List<String> ids = new ArrayList<>();
        for (RegionStateRow row : rows) {
            ids.add(row.worldGuardRegionId());
        }
        return ids;
    }

    @Test
    void derivesEachContractStateFromItsHolder() {
        List<RegionStateRow> rows = page(50, 0);
        Assertions.assertEquals("FOR_SALE", stateOf(rows, "plot_for_sale"));
        Assertions.assertEquals("SOLD", stateOf(rows, "plot_sold"));
        Assertions.assertEquals("FOR_LEASE", stateOf(rows, "plot_for_lease"));
    }

    @Test
    void reportsLeasedOnceTheLeaseholdHasATenant() {
        Assertions.assertNotNull(logic.rentRegion("plot_for_lease", WORLD_ID, TENANT));
        Assertions.assertEquals("LEASED", stateOf(page(50, 0), "plot_for_lease"));
    }

    @Test
    void reportsANullStateForARegisteredRegionCarryingNoContract() {
        Assertions.assertNull(stateOf(page(50, 0), "plot_bare"),
                "a listing must report a contractless region, unlike getAllRegionsWithState which drops it");
    }

    @Test
    void listsEveryRegisteredRegionIncludingTheContractlessOne() {
        Assertions.assertEquals(5, page(50, 0).size());
    }

    @Test
    void pagesInATotalOrderThatNeitherRepeatsNorSkips() {
        List<String> paged = new ArrayList<>();
        for (int offset = 0; offset < 5; offset++) {
            paged.addAll(ids(page(1, offset)));
        }
        Assertions.assertEquals(ids(page(50, 0)), paged);
        Assertions.assertEquals(5, paged.stream().distinct().count());
    }

    @Test
    void narrowsToOneWorld() {
        List<RegionStateRow> rows = pageInWorld(OTHER_WORLD, 50, 0);
        Assertions.assertEquals(List.of("plot_elsewhere"), ids(rows));
        Assertions.assertEquals("FOR_SALE", rows.get(0).state());
    }

    @Test
    void countsOnlyTheRegionsInTheNamedWorld() {
        try (SqlSessionWrapper session = database.openSession(true)) {
            Assertions.assertEquals(4, session.realtyRegionMapper().countByWorld(WORLD_ID));
            Assertions.assertEquals(1, session.realtyRegionMapper().countByWorld(OTHER_WORLD));
            Assertions.assertEquals(5, session.realtyRegionMapper().countAll());
        }
    }
}
