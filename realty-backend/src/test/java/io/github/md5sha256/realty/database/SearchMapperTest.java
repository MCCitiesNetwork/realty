package io.github.md5sha256.realty.database;

import io.github.md5sha256.realty.database.entity.OccupancyFilter;
import io.github.md5sha256.realty.database.entity.SearchResultEntity;
import io.github.md5sha256.realty.database.entity.SearchSort;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

/**
 * Exercises the type axis of {@code SearchMapper}: the difference between what is
 * <em>on the market</em> (a freehold with an asking price) and what <em>exists</em>
 * (every freehold, priced or not). Leaseholds always carry a price, so the axis only
 * bites on the freehold side.
 */
class SearchMapperTest extends AbstractDatabaseTest {

    private static final UUID WORLD_ID = UUID.fromString("8f4d1c2e-0000-0000-0000-000000000003");
    private static final UUID AUTHORITY = UUID.fromString("3a1c88f0-0000-0000-0000-000000000010");
    private static final UUID OWNER = UUID.fromString("3a1c88f0-0000-0000-0000-000000000011");
    private static final UUID LANDLORD = UUID.fromString("3a1c88f0-0000-0000-0000-000000000012");

    @BeforeEach
    void seed() {
        Assertions.assertTrue(logic.createFreehold("plot_listed", WORLD_ID, 5000.0, AUTHORITY, null));
        Assertions.assertTrue(logic.createFreehold("plot_sold", WORLD_ID, null, AUTHORITY, OWNER));
        Assertions.assertTrue(logic.createLeasehold("plot_rental", WORLD_ID, 250.0, 604800L, -1, LANDLORD));
    }

    private static List<SearchResultEntity> search(boolean freehold, boolean leasehold, boolean unpriced,
                                                   double min, double max) {
        try (SqlSessionWrapper session = database.openSession(true)) {
            return session.searchMapper().search(freehold, leasehold, unpriced, null, null, null,
                    min, max, OccupancyFilter.IGNORE, SearchSort.PRICE_DESC, 50, 0);
        }
    }

    private static int count(boolean freehold, boolean leasehold, boolean unpriced, double min, double max) {
        try (SqlSessionWrapper session = database.openSession(true)) {
            return session.searchMapper().searchCount(freehold, leasehold, unpriced, null, null, null,
                    min, max, OccupancyFilter.IGNORE);
        }
    }

    private static List<String> ids(List<SearchResultEntity> rows) {
        return rows.stream().map(SearchResultEntity::worldGuardRegionId).toList();
    }

    @Test
    void saleReturnsOnlyPricedFreeholds() {
        List<SearchResultEntity> rows = search(true, false, false, 0, Double.MAX_VALUE);
        Assertions.assertEquals(List.of("plot_listed"), ids(rows));
        Assertions.assertEquals(1, count(true, false, false, 0, Double.MAX_VALUE));
    }

    @Test
    void allFreeholdsIncludesTheUnpricedOneWithANullPrice() {
        List<SearchResultEntity> rows = search(true, false, true, 0, Double.MAX_VALUE);
        Assertions.assertEquals(List.of("plot_listed", "plot_sold"), ids(rows),
                "price DESC puts the unpriced row last");
        Assertions.assertEquals(5000.0, rows.get(0).price());
        Assertions.assertNull(rows.get(1).price());
        Assertions.assertEquals(2, count(true, false, true, 0, Double.MAX_VALUE));
    }

    @Test
    void priceBoundsApplyOnlyToPricedFreeholdsWhenUnpricedAreIncluded() {
        List<SearchResultEntity> rows = search(true, false, true, 0, 100.0);
        Assertions.assertEquals(List.of("plot_sold"), ids(rows),
                "the listed plot is over the bound; the unpriced one has no price to compare");
        Assertions.assertEquals(1, count(true, false, true, 0, 100.0));
    }

    @Test
    void leaseholdSideIsUnaffectedByTheUnpricedFlag() {
        Assertions.assertEquals(List.of("plot_rental"), ids(search(false, true, false, 0, Double.MAX_VALUE)));
        Assertions.assertEquals(List.of("plot_rental"), ids(search(false, true, true, 0, Double.MAX_VALUE)));
    }

    @Test
    void reportsTheLeaseTermOnLeaseholdRowsAndNothingOnFreeholds() {
        // A rent is per term, and the term is a fact of the contract: a listing card that
        // read "200" alone was a number without a unit.
        List<SearchResultEntity> rows = search(true, true, true, 0, Double.MAX_VALUE);
        for (SearchResultEntity row : rows) {
            if (row.contractType().equals("leasehold")) {
                Assertions.assertNotNull(row.durationSeconds(), row.worldGuardRegionId());
                Assertions.assertTrue(row.durationSeconds() > 0, row.worldGuardRegionId());
            } else {
                Assertions.assertNull(row.durationSeconds(), row.worldGuardRegionId());
            }
        }
    }

    @Test
    void reportsEachRowsStateAlongsideItsPrice() {
        List<SearchResultEntity> rows = search(true, true, true, 0, Double.MAX_VALUE);
        Assertions.assertEquals(List.of("plot_listed", "plot_rental", "plot_sold"), ids(rows));
        Assertions.assertEquals("FOR_SALE", rows.get(0).state());
        Assertions.assertEquals("FOR_LEASE", rows.get(1).state());
        Assertions.assertEquals("SOLD", rows.get(2).state(),
                "a freehold with a title holder is sold, whether or not it still carries a price");
    }

    @Test
    void reportsLeasedOnceTheLeaseholdHasATenant() {
        UUID tenant = UUID.fromString("3a1c88f0-0000-0000-0000-000000000013");
        Assertions.assertNotNull(logic.rentRegion("plot_rental", WORLD_ID, tenant));
        List<SearchResultEntity> rows = search(false, true, false, 0, Double.MAX_VALUE);
        Assertions.assertEquals("LEASED", rows.get(0).state());
    }

    @Test
    void bothSidesUnionWithUnpricedFreeholds() {
        List<SearchResultEntity> rows = search(true, true, true, 0, Double.MAX_VALUE);
        Assertions.assertEquals(List.of("plot_listed", "plot_rental", "plot_sold"), ids(rows));
        Assertions.assertEquals(3, count(true, true, true, 0, Double.MAX_VALUE));
    }
}
