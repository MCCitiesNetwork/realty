package io.github.md5sha256.realty.rest;

import io.github.md5sha256.realty.database.entity.RegionStateRow;
import io.github.md5sha256.realty.database.entity.RealtyWorldEntity;
import io.github.md5sha256.realty.database.entity.SearchResultEntity;
import io.javalin.testtools.JavalinTest;
import okhttp3.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

/**
 * {@code state} on the two browse-listing row shapes.
 *
 * <p>Without it a consumer rendering a grid has to call {@code /v1/region} once per
 * row purely to learn whether a plot is sold or still for sale.</p>
 */
class ListRowStateTest {

    private static final UUID WORLD_ID = UUID.fromString("8f4d0000-0000-0000-0000-000000000001");
    private static final List<RealtyWorldEntity> WORLDS =
            List.of(new RealtyWorldEntity(WORLD_ID, "world"));

    @Test
    void reportsEachListedRegionsState() {
        List<RegionStateRow> rows = List.of(
                new RegionStateRow(1, "plot_sold", WORLD_ID, "SOLD"),
                new RegionStateRow(2, "plot_open", WORLD_ID, "FOR_SALE"));
        RealtyRestServer server = TestServers.withRegionListStates(rows, WORLDS);
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/regions");
            Assertions.assertEquals(200, response.code());
            String body = response.body().string();
            Assertions.assertTrue(body.contains("\"worldGuardRegionId\":\"plot_sold\""), body);
            Assertions.assertTrue(body.contains("\"state\":\"SOLD\""), "expected SOLD in: " + body);
            Assertions.assertTrue(body.contains("\"state\":\"FOR_SALE\""), "expected FOR_SALE in: " + body);
        });
    }

    @Test
    void reportsANullStateForARegionCarryingNoContract() {
        List<RegionStateRow> rows = List.of(
                new RegionStateRow(1, "plot_bare", WORLD_ID, null));
        RealtyRestServer server = TestServers.withRegionListStates(rows, WORLDS);
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/regions");
            Assertions.assertEquals(200, response.code());
            String body = response.body().string();
            Assertions.assertTrue(body.contains("\"state\":null"),
                    "a registered region with no contract has no state: " + body);
        });
    }

    @Test
    void reportsEachSearchResultsState() {
        SearchResultEntity row =
                new SearchResultEntity("plot_12", WORLD_ID, "freehold", 5000.0, "FOR_SALE");
        RealtyRestServer server = TestServers.withSearch(
                new TestServers.SearchStub(List.of(row), 1), WORLDS);
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/regions/search");
            Assertions.assertEquals(200, response.code());
            Assertions.assertTrue(response.body().string().contains("\"state\":\"FOR_SALE\""));
        });
    }
}
