package io.github.md5sha256.realty.database;

import io.github.md5sha256.realty.database.entity.OccupancyFilter;
import io.github.md5sha256.realty.database.entity.SearchResultEntity;
import io.github.md5sha256.realty.database.entity.SearchSort;
import org.apache.ibatis.session.SqlSession;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * The tag axis of {@code SearchMapper}: any of the tags, all of them, and none of a
 * second set.
 */
class SearchTagsMapperTest extends AbstractDatabaseTest {

    private static final UUID WORLD_ID = UUID.fromString("8f4d1c2e-0000-0000-0000-000000000006");
    private static final UUID AUTHORITY = UUID.fromString("3a1c88f0-0000-0000-0000-000000000040");

    @BeforeEach
    void seed() {
        // shop_water: commercial + waterfront; shop_only: commercial; house: residential.
        Assertions.assertTrue(logic.createFreehold("shop_water", WORLD_ID, 300.0, AUTHORITY, null));
        Assertions.assertTrue(logic.createFreehold("shop_only", WORLD_ID, 200.0, AUTHORITY, null));
        Assertions.assertTrue(logic.createFreehold("house", WORLD_ID, 100.0, AUTHORITY, null));
        try (SqlSessionWrapper wrapper = database.openSession();
             SqlSession session = wrapper.session()) {
            wrapper.regionTagMapper().insert("commercial", "shop_water");
            wrapper.regionTagMapper().insert("waterfront", "shop_water");
            wrapper.regionTagMapper().insert("commercial", "shop_only");
            wrapper.regionTagMapper().insert("residential", "house");
            session.commit();
        }
    }

    private static List<String> search(@Nullable Collection<String> tags, @Nullable Collection<String> excluded,
                                       boolean all) {
        try (SqlSessionWrapper session = database.openSession(true)) {
            List<SearchResultEntity> rows = session.searchMapper().search(true, true, false, null, tags, excluded, all,
                    0, Double.MAX_VALUE, OccupancyFilter.IGNORE, SearchSort.PRICE_DESC, 50, 0);
            int count = session.searchMapper().searchCount(true, true, false, null, tags, excluded, all,
                    0, Double.MAX_VALUE, OccupancyFilter.IGNORE);
            Assertions.assertEquals(rows.size(), count, "the count must agree with the rows");
            return rows.stream().map(SearchResultEntity::worldGuardRegionId).toList();
        }
    }

    @Test
    void anyOfTheTagsIsAUnion() {
        Assertions.assertEquals(List.of("shop_water", "shop_only", "house"),
                search(List.of("commercial", "residential"), null, false));
    }

    @Test
    void allOfTheTagsIsAnIntersection() {
        Assertions.assertEquals(List.of("shop_water"),
                search(List.of("commercial", "waterfront"), null, true));
    }

    @Test
    void allOfOneTagIsTheSameAsAnyOfIt() {
        Assertions.assertEquals(search(List.of("commercial"), null, false),
                search(List.of("commercial"), null, true));
    }

    @Test
    void allOfATagNothingCarriesMatchesNothing() {
        Assertions.assertEquals(List.of(), search(List.of("commercial", "island"), null, true));
    }

    @Test
    void excludedTagsTakeTheirRegionsOut() {
        Assertions.assertEquals(List.of("shop_only", "house"),
                search(null, List.of("waterfront"), false));
        Assertions.assertEquals(List.of("shop_only"),
                search(List.of("commercial"), List.of("waterfront"), false));
    }

    @Test
    void matchingAllWithoutTagsFiltersNothing() {
        Assertions.assertEquals(List.of("shop_water", "shop_only", "house"), search(null, null, true));
    }
}
