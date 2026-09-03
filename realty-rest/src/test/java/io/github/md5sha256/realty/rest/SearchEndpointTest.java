package io.github.md5sha256.realty.rest;

import io.github.md5sha256.realty.database.entity.OccupancyFilter;
import io.github.md5sha256.realty.database.entity.RealtyWorldEntity;
import io.github.md5sha256.realty.database.entity.SearchResultEntity;
import io.github.md5sha256.realty.database.entity.SearchSort;
import io.javalin.testtools.JavalinTest;
import okhttp3.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

class SearchEndpointTest {

    private static final UUID WORLD_ID = UUID.fromString("8f4d1c2e-0000-0000-0000-000000000001");
    private static final String WORLD_NAME = "My World";

    private static List<RealtyWorldEntity> worlds() {
        return List.of(new RealtyWorldEntity(WORLD_ID, WORLD_NAME));
    }

    private static TestServers.SearchStub stubWithOneResult() {
        SearchResultEntity row = new SearchResultEntity("plot_12", WORLD_ID, "freehold", 5000.0, "FOR_SALE");
        return new TestServers.SearchStub(List.of(row), 1);
    }

    // --- type ---------------------------------------------------------------

    @Test
    void defaultsToBothContractTypes() {
        TestServers.SearchStub stub = stubWithOneResult();
        JavalinTest.test(TestServers.withSearch(stub, worlds()).javalin(), (server, client) -> {
            Assertions.assertEquals(200, client.get("/v1/regions/search").code());
            Assertions.assertTrue(stub.includeFreehold, "type defaults to all, so freehold is included");
            Assertions.assertTrue(stub.includeLeasehold, "type defaults to all, so leasehold is included");
        });
    }

    @Test
    void defaultsToTheMarketViewOfFreeholds() {
        TestServers.SearchStub stub = stubWithOneResult();
        JavalinTest.test(TestServers.withSearch(stub, worlds()).javalin(), (server, client) -> {
            Assertions.assertEquals(200, client.get("/v1/regions/search").code());
            Assertions.assertFalse(stub.includeUnpricedFreehold,
                    "all means on the market, so unlisted freeholds stay out");
        });
    }

    @Test
    void typeSaleSelectsPricedFreeholdOnly() {
        TestServers.SearchStub stub = stubWithOneResult();
        JavalinTest.test(TestServers.withSearch(stub, worlds()).javalin(), (server, client) -> {
            Assertions.assertEquals(200, client.get("/v1/regions/search?type=sale").code());
            Assertions.assertTrue(stub.includeFreehold);
            Assertions.assertFalse(stub.includeLeasehold);
            Assertions.assertFalse(stub.includeUnpricedFreehold);
        });
    }

    @Test
    void typeFreeholdSelectsEveryFreeholdIncludingUnpriced() {
        TestServers.SearchStub stub = stubWithOneResult();
        JavalinTest.test(TestServers.withSearch(stub, worlds()).javalin(), (server, client) -> {
            Assertions.assertEquals(200, client.get("/v1/regions/search?type=freehold").code());
            Assertions.assertTrue(stub.includeFreehold);
            Assertions.assertFalse(stub.includeLeasehold);
            Assertions.assertTrue(stub.includeUnpricedFreehold);
        });
    }

    @Test
    void typeLeaseholdSelectsLeaseholdOnly() {
        TestServers.SearchStub stub = stubWithOneResult();
        JavalinTest.test(TestServers.withSearch(stub, worlds()).javalin(), (server, client) -> {
            Assertions.assertEquals(200, client.get("/v1/regions/search?type=leasehold").code());
            Assertions.assertFalse(stub.includeFreehold);
            Assertions.assertTrue(stub.includeLeasehold);
            Assertions.assertFalse(stub.includeUnpricedFreehold);
        });
    }

    @Test
    void anUnpricedFreeholdIsRenderedWithANullPrice() {
        SearchResultEntity row = new SearchResultEntity("plot_sold", WORLD_ID, "freehold", null, "SOLD");
        TestServers.SearchStub stub = new TestServers.SearchStub(List.of(row), 1);
        JavalinTest.test(TestServers.withSearch(stub, worlds()).javalin(), (server, client) -> {
            String body = client.get("/v1/regions/search?type=freehold").body().string();
            Assertions.assertTrue(body.contains("\"price\":null"), body);
        });
    }

    @Test
    void typeRentSelectsLeaseholdOnly() {
        TestServers.SearchStub stub = stubWithOneResult();
        JavalinTest.test(TestServers.withSearch(stub, worlds()).javalin(), (server, client) -> {
            Assertions.assertEquals(200, client.get("/v1/regions/search?type=rent").code());
            Assertions.assertFalse(stub.includeFreehold);
            Assertions.assertTrue(stub.includeLeasehold);
        });
    }

    @Test
    void typeAllSelectsBoth() {
        TestServers.SearchStub stub = stubWithOneResult();
        JavalinTest.test(TestServers.withSearch(stub, worlds()).javalin(), (server, client) -> {
            Assertions.assertEquals(200, client.get("/v1/regions/search?type=all").code());
            Assertions.assertTrue(stub.includeFreehold);
            Assertions.assertTrue(stub.includeLeasehold);
        });
    }

    @Test
    void rejectsAnUnknownType() {
        TestServers.SearchStub stub = stubWithOneResult();
        JavalinTest.test(TestServers.withSearch(stub, worlds()).javalin(), (server, client) -> {
            Response response = client.get("/v1/regions/search?type=mortgage");
            Assertions.assertEquals(400, response.code());
            Assertions.assertTrue(response.body().string().contains("INVALID_TYPE"));
        });
    }

    // --- world --------------------------------------------------------------

    @Test
    void searchesEveryWorldWhenNoWorldIsGiven() {
        TestServers.SearchStub stub = stubWithOneResult();
        JavalinTest.test(TestServers.withSearch(stub, worlds()).javalin(), (server, client) -> {
            Assertions.assertEquals(200, client.get("/v1/regions/search").code());
            Assertions.assertNull(stub.worldId, "an absent world must not become a filter");
        });
    }

    @Test
    void resolvesAWorldNameToItsIdAndFiltersOnIt() {
        TestServers.SearchStub stub = stubWithOneResult();
        JavalinTest.test(TestServers.withSearch(stub, worlds()).javalin(), (server, client) -> {
            Assertions.assertEquals(200, client.get("/v1/regions/search?world=My%20World").code());
            Assertions.assertEquals(WORLD_ID, stub.worldId);
        });
    }

    @Test
    void acceptsAWorldUuidDirectly() {
        TestServers.SearchStub stub = stubWithOneResult();
        JavalinTest.test(TestServers.withSearch(stub, worlds()).javalin(), (server, client) -> {
            Assertions.assertEquals(200, client.get("/v1/regions/search?world=" + WORLD_ID).code());
            Assertions.assertEquals(WORLD_ID, stub.worldId);
        });
    }

    @Test
    void returns404ForAnUnknownWorldName() {
        TestServers.SearchStub stub = stubWithOneResult();
        JavalinTest.test(TestServers.withSearch(stub, worlds()).javalin(), (server, client) -> {
            Response response = client.get("/v1/regions/search?world=Atlantis");
            Assertions.assertEquals(404, response.code());
            Assertions.assertTrue(response.body().string().contains("WORLD_NOT_FOUND"));
        });
    }

    // --- price --------------------------------------------------------------

    @Test
    void passesBothPriceBoundsThrough() {
        TestServers.SearchStub stub = stubWithOneResult();
        JavalinTest.test(TestServers.withSearch(stub, worlds()).javalin(), (server, client) -> {
            Assertions.assertEquals(200,
                    client.get("/v1/regions/search?minPrice=100&maxPrice=900.5").code());
            Assertions.assertEquals(100.0, stub.minPrice);
            Assertions.assertEquals(900.5, stub.maxPrice);
        });
    }

    @Test
    void anAbsentPriceBoundIsOpenEnded() {
        TestServers.SearchStub stub = stubWithOneResult();
        JavalinTest.test(TestServers.withSearch(stub, worlds()).javalin(), (server, client) -> {
            Assertions.assertEquals(200, client.get("/v1/regions/search").code());
            Assertions.assertEquals(0.0, stub.minPrice);
            Assertions.assertEquals(Double.MAX_VALUE, stub.maxPrice);
        });
    }

    @Test
    void rejectsANonNumericPrice() {
        TestServers.SearchStub stub = stubWithOneResult();
        JavalinTest.test(TestServers.withSearch(stub, worlds()).javalin(), (server, client) -> {
            Response response = client.get("/v1/regions/search?minPrice=cheap");
            Assertions.assertEquals(400, response.code());
            Assertions.assertTrue(response.body().string().contains("INVALID_PRICE"));
        });
    }

    @Test
    void rejectsANonFinitePrice() {
        TestServers.SearchStub stub = stubWithOneResult();
        JavalinTest.test(TestServers.withSearch(stub, worlds()).javalin(), (server, client) -> {
            Response response = client.get("/v1/regions/search?maxPrice=Infinity");
            Assertions.assertEquals(400, response.code());
            Assertions.assertTrue(response.body().string().contains("INVALID_PRICE"));
        });
    }

    @Test
    void rejectsANegativePrice() {
        TestServers.SearchStub stub = stubWithOneResult();
        JavalinTest.test(TestServers.withSearch(stub, worlds()).javalin(), (server, client) -> {
            Response response = client.get("/v1/regions/search?minPrice=-1");
            Assertions.assertEquals(400, response.code());
            Assertions.assertTrue(response.body().string().contains("INVALID_PRICE"));
        });
    }

    @Test
    void rejectsAMinimumAboveTheMaximum() {
        TestServers.SearchStub stub = stubWithOneResult();
        JavalinTest.test(TestServers.withSearch(stub, worlds()).javalin(), (server, client) -> {
            Response response = client.get("/v1/regions/search?minPrice=900&maxPrice=100");
            Assertions.assertEquals(400, response.code());
            Assertions.assertTrue(response.body().string().contains("INVALID_PRICE"));
        });
    }

    @Test
    void acceptsAMinimumEqualToTheMaximum() {
        TestServers.SearchStub stub = stubWithOneResult();
        JavalinTest.test(TestServers.withSearch(stub, worlds()).javalin(), (server, client) -> {
            Assertions.assertEquals(200,
                    client.get("/v1/regions/search?minPrice=500&maxPrice=500").code());
            Assertions.assertEquals(500.0, stub.minPrice);
            Assertions.assertEquals(500.0, stub.maxPrice);
        });
    }

    // --- tags ---------------------------------------------------------------

    @Test
    void passesEveryRepeatedTagThrough() {
        TestServers.SearchStub stub = stubWithOneResult();
        JavalinTest.test(TestServers.withSearch(stub, worlds()).javalin(), (server, client) -> {
            Assertions.assertEquals(200,
                    client.get("/v1/regions/search?tag=commercial&tag=waterfront").code());
            Assertions.assertNotNull(stub.tagIds);
            Assertions.assertEquals(List.of("commercial", "waterfront"), List.copyOf(stub.tagIds));
        });
    }

    @Test
    void noTagMeansNoTagFilter() {
        TestServers.SearchStub stub = stubWithOneResult();
        JavalinTest.test(TestServers.withSearch(stub, worlds()).javalin(), (server, client) -> {
            Assertions.assertEquals(200, client.get("/v1/regions/search").code());
            Assertions.assertNull(stub.tagIds, "an absent tag must not become an empty-match filter");
        });
    }

    @Test
    void neverExcludesTags() {
        TestServers.SearchStub stub = stubWithOneResult();
        JavalinTest.test(TestServers.withSearch(stub, worlds()).javalin(), (server, client) -> {
            Assertions.assertEquals(200, client.get("/v1/regions/search?tag=commercial").code());
            Assertions.assertNull(stub.excludedTagIds,
                    "this endpoint is include-only; exclusion is not exposed");
        });
    }

    // --- occupancy ----------------------------------------------------------

    @Test
    void occupancyDefaultsToIgnore() {
        TestServers.SearchStub stub = stubWithOneResult();
        JavalinTest.test(TestServers.withSearch(stub, worlds()).javalin(), (server, client) -> {
            Assertions.assertEquals(200, client.get("/v1/regions/search").code());
            Assertions.assertEquals(OccupancyFilter.IGNORE, stub.occupancy);
        });
    }

    @Test
    void mapsOccupiedAndUnoccupied() {
        TestServers.SearchStub occupied = stubWithOneResult();
        JavalinTest.test(TestServers.withSearch(occupied, worlds()).javalin(), (server, client) -> {
            client.get("/v1/regions/search?occupancy=occupied");
            Assertions.assertEquals(OccupancyFilter.OCCUPIED, occupied.occupancy);
        });
        TestServers.SearchStub unoccupied = stubWithOneResult();
        JavalinTest.test(TestServers.withSearch(unoccupied, worlds()).javalin(), (server, client) -> {
            client.get("/v1/regions/search?occupancy=unoccupied");
            Assertions.assertEquals(OccupancyFilter.UNOCCUPIED, unoccupied.occupancy);
        });
    }

    @Test
    void rejectsAnUnknownOccupancy() {
        TestServers.SearchStub stub = stubWithOneResult();
        JavalinTest.test(TestServers.withSearch(stub, worlds()).javalin(), (server, client) -> {
            Response response = client.get("/v1/regions/search?occupancy=vacant");
            Assertions.assertEquals(400, response.code());
            Assertions.assertTrue(response.body().string().contains("INVALID_OCCUPANCY"));
        });
    }

    // --- sort ---------------------------------------------------------------

    @Test
    void sortDefaultsToPriceDescending() {
        TestServers.SearchStub stub = stubWithOneResult();
        JavalinTest.test(TestServers.withSearch(stub, worlds()).javalin(), (server, client) -> {
            Assertions.assertEquals(200, client.get("/v1/regions/search").code());
            Assertions.assertEquals(SearchSort.PRICE_DESC, stub.sort);
        });
    }

    @Test
    void mapsBothSortDirections() {
        TestServers.SearchStub asc = stubWithOneResult();
        JavalinTest.test(TestServers.withSearch(asc, worlds()).javalin(), (server, client) -> {
            client.get("/v1/regions/search?sort=price_asc");
            Assertions.assertEquals(SearchSort.PRICE_ASC, asc.sort);
        });
        TestServers.SearchStub desc = stubWithOneResult();
        JavalinTest.test(TestServers.withSearch(desc, worlds()).javalin(), (server, client) -> {
            client.get("/v1/regions/search?sort=price_desc");
            Assertions.assertEquals(SearchSort.PRICE_DESC, desc.sort);
        });
    }

    @Test
    void rejectsAnUnknownSort() {
        TestServers.SearchStub stub = stubWithOneResult();
        JavalinTest.test(TestServers.withSearch(stub, worlds()).javalin(), (server, client) -> {
            Response response = client.get("/v1/regions/search?sort=newest");
            Assertions.assertEquals(400, response.code());
            Assertions.assertTrue(response.body().string().contains("INVALID_SORT"));
        });
    }

    // --- paging -------------------------------------------------------------

    @Test
    void reportsTotalPagesFromTheTotalCount() {
        TestServers.SearchStub stub = new TestServers.SearchStub(List.of(), 42);
        JavalinTest.test(TestServers.withSearch(stub, worlds()).javalin(), (server, client) -> {
            String body = client.get("/v1/regions/search?pageSize=10").body().string();
            Assertions.assertTrue(body.contains("\"totalCount\":42"), body);
            Assertions.assertTrue(body.contains("\"totalPages\":5"), body);
        });
    }

    @Test
    void translatesPageToAnOffset() {
        TestServers.SearchStub stub = new TestServers.SearchStub(List.of(), 42);
        JavalinTest.test(TestServers.withSearch(stub, worlds()).javalin(), (server, client) -> {
            Assertions.assertEquals(200, client.get("/v1/regions/search?page=3&pageSize=10").code());
            Assertions.assertEquals(10, stub.limit);
            Assertions.assertEquals(20, stub.offset);
        });
    }

    @Test
    void clampsPageSizeToTheConfiguredMaximum() {
        TestServers.SearchStub stub = stubWithOneResult();
        RealtyRestServer server = TestServers.withSearch(stub, worlds(), 10, List.of());
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            String body = client.get("/v1/regions/search?pageSize=500").body().string();
            Assertions.assertTrue(body.contains("\"pageSize\":10"), body);
            Assertions.assertEquals(10, stub.limit);
        });
    }

    @Test
    void aPageBeyondTheLastIs200WithNoResults() {
        TestServers.SearchStub stub = new TestServers.SearchStub(List.of(), 5);
        JavalinTest.test(TestServers.withSearch(stub, worlds()).javalin(), (server, client) -> {
            Response response = client.get("/v1/regions/search?page=99&pageSize=10");
            Assertions.assertEquals(200, response.code());
            Assertions.assertTrue(response.body().string().contains("\"results\":[]"));
        });
    }

    @Test
    void rejectsAPageBelowOne() {
        TestServers.SearchStub stub = stubWithOneResult();
        JavalinTest.test(TestServers.withSearch(stub, worlds()).javalin(), (server, client) -> {
            Response response = client.get("/v1/regions/search?page=0");
            Assertions.assertEquals(400, response.code());
            Assertions.assertTrue(response.body().string().contains("INVALID_PAGE"));
        });
    }

    @Test
    void rejectsANonIntegerPageSize() {
        TestServers.SearchStub stub = stubWithOneResult();
        JavalinTest.test(TestServers.withSearch(stub, worlds()).javalin(), (server, client) -> {
            Response response = client.get("/v1/regions/search?pageSize=lots");
            Assertions.assertEquals(400, response.code());
            Assertions.assertTrue(response.body().string().contains("INVALID_PAGE_SIZE"));
        });
    }

    // --- response shape -----------------------------------------------------

    @Test
    void aResultCarriesTheFullWorldRefNotABareUuid() {
        TestServers.SearchStub stub = stubWithOneResult();
        JavalinTest.test(TestServers.withSearch(stub, worlds()).javalin(), (server, client) -> {
            String body = client.get("/v1/regions/search").body().string();
            Assertions.assertTrue(body.contains("\"worldGuardRegionId\":\"plot_12\""), body);
            Assertions.assertTrue(body.contains("\"contractType\":\"freehold\""), body);
            Assertions.assertTrue(body.contains("\"price\":5000.0"), body);
            Assertions.assertTrue(body.contains("\"id\":\"" + WORLD_ID + "\""), body);
            Assertions.assertTrue(body.contains("\"name\":\"" + WORLD_NAME + "\""), body);
        });
    }

    @Test
    void noMatchesIs200WithAnEmptyArray() {
        JavalinTest.test(TestServers.withSearch(TestServers.SearchStub.empty(), worlds()).javalin(),
                (server, client) -> {
                    Response response = client.get("/v1/regions/search?type=rent");
                    Assertions.assertEquals(200, response.code());
                    String body = response.body().string();
                    Assertions.assertTrue(body.contains("\"results\":[]"), body);
                    Assertions.assertTrue(body.contains("\"totalCount\":0"), body);
                });
    }

    // --- CORS ---------------------------------------------------------------

    /**
     * The SPA is served from a different origin, so every one of its requests is
     * preceded by this preflight. Without the allowlist the browser blocks the
     * real request, and nothing on the server side ever reports an error.
     */
    @Test
    void allowsAPreflightFromAConfiguredOrigin() {
        RealtyRestServer server = TestServers.withSearch(
                stubWithOneResult(), worlds(), 100, List.of("http://localhost:5173"));
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.request("/v1/regions/search", builder -> builder
                    .method("OPTIONS", null)
                    .header("Origin", "http://localhost:5173")
                    .header("Access-Control-Request-Method", "GET"));
            Assertions.assertEquals("http://localhost:5173",
                    response.header("Access-Control-Allow-Origin"));
        });
    }

    @Test
    void refusesAPreflightFromAnUnlistedOrigin() {
        RealtyRestServer server = TestServers.withSearch(
                stubWithOneResult(), worlds(), 100, List.of("http://localhost:5173"));
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.request("/v1/regions/search", builder -> builder
                    .method("OPTIONS", null)
                    .header("Origin", "http://evil.example")
                    .header("Access-Control-Request-Method", "GET"));
            Assertions.assertNull(response.header("Access-Control-Allow-Origin"),
                    "an unlisted origin must not be echoed back as allowed");
        });
    }

    @Test
    void sendsNoCorsHeadersWhenNoOriginsAreConfigured() {
        RealtyRestServer server = TestServers.withSearch(stubWithOneResult(), worlds());
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.request("/v1/regions/search", builder -> builder
                    .get()
                    .header("Origin", "http://localhost:5173"));
            Assertions.assertNull(response.header("Access-Control-Allow-Origin"),
                    "CORS is off by default; nothing should be allowed implicitly");
        });
    }

}
