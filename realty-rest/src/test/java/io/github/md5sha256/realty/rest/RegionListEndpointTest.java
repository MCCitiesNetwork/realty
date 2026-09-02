package io.github.md5sha256.realty.rest;

import io.github.md5sha256.realty.database.entity.RealtyRegionEntity;
import io.github.md5sha256.realty.database.entity.RealtyWorldEntity;
import io.javalin.testtools.JavalinTest;
import okhttp3.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

class RegionListEndpointTest {

    private static final UUID WORLD_ID = UUID.fromString("8f4d1c2e-0000-0000-0000-000000000001");

    private static List<RealtyWorldEntity> worlds() {
        return List.of(new RealtyWorldEntity(WORLD_ID, "world"));
    }

    /**
     * Regions named {@code plot_01}..{@code plot_NN}, already in the order the
     * mapper's {@code ORDER BY} produces, so a test can name the row it expects on
     * a given page.
     */
    private static List<RealtyRegionEntity> regions(int count) {
        List<RealtyRegionEntity> regions = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            regions.add(new RealtyRegionEntity(i, String.format("plot_%02d", i), WORLD_ID));
        }
        return regions;
    }

    @Test
    void listsEveryRegionWithItsWorld() {
        RealtyRestServer server = TestServers.withRegionList(regions(3), worlds());
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/regions");
            Assertions.assertEquals(200, response.code());
            String body = response.body().string();
            Assertions.assertTrue(body.contains("\"totalCount\":3"), body);
            Assertions.assertTrue(body.contains("\"worldGuardRegionId\":\"plot_01\""), body);
            Assertions.assertTrue(body.contains("\"id\":\"" + WORLD_ID + "\""), body);
            Assertions.assertTrue(body.contains("\"name\":\"world\""), body);
        });
    }

    @Test
    void listsRegionsWithNoContractAtAll() {
        // The distinction from /v1/regions/search: a region Realty has registered
        // but which carries no contract has no search result, and must still be
        // listed here. The stub backend throws from every RealtyBackend method, so
        // a handler that consulted contracts at all would fail this test.
        RealtyRestServer server = TestServers.withRegionList(regions(1), worlds());
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Assertions.assertEquals(200, client.get("/v1/regions").code());
        });
    }

    @Test
    void reportsTotalPagesFromTheTotalCount() {
        RealtyRestServer server = TestServers.withRegionList(regions(42), worlds());
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            String body = client.get("/v1/regions?pageSize=10").body().string();
            Assertions.assertTrue(body.contains("\"totalCount\":42"), body);
            Assertions.assertTrue(body.contains("\"totalPages\":5"), body);
        });
    }

    @Test
    void consecutivePagesNeitherRepeatNorSkipARow() {
        RealtyRestServer server = TestServers.withRegionList(regions(6), worlds());
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            String first = client.get("/v1/regions?page=1&pageSize=2").body().string();
            String second = client.get("/v1/regions?page=2&pageSize=2").body().string();
            String third = client.get("/v1/regions?page=3&pageSize=2").body().string();

            Assertions.assertTrue(first.contains("plot_01") && first.contains("plot_02"), first);
            Assertions.assertTrue(second.contains("plot_03") && second.contains("plot_04"), second);
            Assertions.assertTrue(third.contains("plot_05") && third.contains("plot_06"), third);

            Assertions.assertFalse(second.contains("plot_02"), "page 2 repeats a page 1 row: " + second);
            Assertions.assertFalse(second.contains("plot_05"), "page 2 reaches into page 3: " + second);
        });
    }

    @Test
    void aPartialLastPageIsReturnedWhole() {
        RealtyRestServer server = TestServers.withRegionList(regions(5), worlds());
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            String body = client.get("/v1/regions?page=3&pageSize=2").body().string();
            Assertions.assertTrue(body.contains("plot_05"), body);
            Assertions.assertTrue(body.contains("\"totalPages\":3"), body);
        });
    }

    @Test
    void clampsPageSizeToTheConfiguredMaximum() {
        RealtyRestServer server = TestServers.withRegionList(regions(30), worlds(), 10);
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            String body = client.get("/v1/regions?pageSize=500").body().string();
            Assertions.assertTrue(body.contains("\"pageSize\":10"), body);
            Assertions.assertFalse(body.contains("plot_11"), "more than the clamp was returned: " + body);
        });
    }

    @Test
    void aPageBeyondTheLastIs200WithNoRegions() {
        RealtyRestServer server = TestServers.withRegionList(regions(3), worlds());
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/regions?page=99&pageSize=10");
            Assertions.assertEquals(200, response.code());
            Assertions.assertTrue(response.body().string().contains("\"regions\":[]"));
        });
    }

    @Test
    void noRegisteredRegionsIs200WithAnEmptyArray() {
        RealtyRestServer server = TestServers.withRegionList(List.of(), worlds());
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/regions");
            Assertions.assertEquals(200, response.code());
            String body = response.body().string();
            Assertions.assertTrue(body.contains("\"regions\":[]"), body);
            Assertions.assertTrue(body.contains("\"totalCount\":0"), body);
        });
    }

    @Test
    void aRegionInAnUnknownWorldStillCarriesAWorldRef() {
        UUID missing = UUID.fromString("8f4d1c2e-9999-0000-0000-000000000009");
        List<RealtyRegionEntity> regions =
                List.of(new RealtyRegionEntity(1, "orphaned_plot", missing));
        RealtyRestServer server = TestServers.withRegionList(regions, List.of());
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            String body = client.get("/v1/regions").body().string();
            Assertions.assertTrue(body.contains("\"id\":\"" + missing + "\""), body);
            Assertions.assertTrue(body.contains("\"name\":null"), body);
        });
    }

    @Test
    void rejectsAPageBelowOne() {
        RealtyRestServer server = TestServers.withRegionList(regions(3), worlds());
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/regions?page=0");
            Assertions.assertEquals(400, response.code());
            Assertions.assertTrue(response.body().string().contains("INVALID_PAGE"));
        });
    }

    @Test
    void rejectsANonIntegerPageSize() {
        RealtyRestServer server = TestServers.withRegionList(regions(3), worlds());
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/regions?pageSize=lots");
            Assertions.assertEquals(400, response.code());
            Assertions.assertTrue(response.body().string().contains("INVALID_PAGE_SIZE"));
        });
    }

    /**
     * The listing and the single-region lookup are now different endpoints. A
     * request carrying {@code world}/{@code region} to the listing is a caller
     * still on the old path shape, and must not be silently served a listing.
     */
    @Test
    void theListingIgnoresSingleRegionParametersRatherThanLookingOneUp() {
        RealtyRestServer server = TestServers.withRegionList(regions(3), worlds());
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            String body = client.get("/v1/regions?world=world&region=plot_01").body().string();
            Assertions.assertTrue(body.contains("\"totalCount\":3"), body);
            Assertions.assertFalse(body.contains("\"freehold\""),
                    "the listing must not return single-region detail: " + body);
        });
    }

}
