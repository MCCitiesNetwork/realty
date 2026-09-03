package io.github.md5sha256.realty.rest;

import io.github.md5sha256.realty.database.entity.RealtyRegionEntity;
import io.github.md5sha256.realty.database.entity.RealtyWorldEntity;
import io.javalin.testtools.JavalinTest;
import io.javalin.testtools.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

/**
 * The {@code world} filter on {@code GET /v1/regions}.
 */
class RegionListWorldFilterTest {

    private static final UUID OVERWORLD = UUID.fromString("aaaa0000-0000-0000-0000-000000000001");
    private static final UUID NETHER = UUID.fromString("bbbb0000-0000-0000-0000-000000000002");

    private static final List<RealtyWorldEntity> WORLDS = List.of(
            new RealtyWorldEntity(OVERWORLD, "world"),
            new RealtyWorldEntity(NETHER, "world_nether"));

    private static final List<RealtyRegionEntity> REGIONS = List.of(
            new RealtyRegionEntity(1, "plot_a", OVERWORLD),
            new RealtyRegionEntity(2, "plot_b", NETHER),
            new RealtyRegionEntity(3, "plot_c", OVERWORLD));

    @Test
    void listsOnlyTheRegionsInTheNamedWorld() {
        RealtyRestServer server = TestServers.withRegionList(REGIONS, WORLDS);
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/regions?world=world");
            Assertions.assertEquals(200, response.code());
            String body = response.body().string();
            Assertions.assertTrue(body.contains("plot_a"), "expected plot_a in: " + body);
            Assertions.assertTrue(body.contains("plot_c"), "expected plot_c in: " + body);
            Assertions.assertFalse(body.contains("plot_b"), "expected plot_b filtered out of: " + body);
        });
    }

    @Test
    void countsOnlyTheFilteredRegions() {
        RealtyRestServer server = TestServers.withRegionList(REGIONS, WORLDS);
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/regions?world=world_nether");
            String body = response.body().string();
            Assertions.assertTrue(body.contains("\"totalCount\":1"),
                    "expected the count to reflect the filter, got: " + body);
        });
    }

    @Test
    void acceptsAWorldUuidAsWellAsAName() {
        RealtyRestServer server = TestServers.withRegionList(REGIONS, WORLDS);
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/regions?world=" + NETHER);
            Assertions.assertEquals(200, response.code());
            String body = response.body().string();
            Assertions.assertTrue(body.contains("plot_b"), "expected plot_b in: " + body);
            Assertions.assertFalse(body.contains("plot_a"), "expected plot_a filtered out of: " + body);
        });
    }

    @Test
    void rejectsAWorldRealtyHasNeverSeen() {
        RealtyRestServer server = TestServers.withRegionList(REGIONS, WORLDS);
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/regions?world=atlantis");
            Assertions.assertEquals(404, response.code());
            Assertions.assertTrue(response.body().string().contains("WORLD_NOT_FOUND"));
        });
    }

    @Test
    void listsEveryWorldsRegionsWhenTheFilterIsAbsent() {
        RealtyRestServer server = TestServers.withRegionList(REGIONS, WORLDS);
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/regions");
            String body = response.body().string();
            Assertions.assertTrue(body.contains("\"totalCount\":3"),
                    "expected every region counted, got: " + body);
        });
    }
}
