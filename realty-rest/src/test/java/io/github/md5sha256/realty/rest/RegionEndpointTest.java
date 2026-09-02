package io.github.md5sha256.realty.rest;

import io.javalin.testtools.JavalinTest;
import okhttp3.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class RegionEndpointTest {

    @Test
    void returnsAForSaleFreeholdRegion() {
        RealtyRestServer server = TestServers.withForSaleRegion();
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/region?world=world&region=downtown_plot_14");
            Assertions.assertEquals(200, response.code());
            String body = response.body().string();
            Assertions.assertTrue(body.contains("\"worldGuardRegionId\":\"downtown_plot_14\""));
            Assertions.assertTrue(body.contains("\"price\":25000.0"));
            Assertions.assertTrue(body.contains("\"leasehold\":null"));
            Assertions.assertTrue(body.contains("\"dimensions\":null"));
        });
    }

    @Test
    void resolvesAWorldNameContainingASpace() {
        RealtyRestServer server = TestServers.withRegionInWorldNamedMyWorld();
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Assertions.assertEquals(200,
                    client.get("/v1/region?world=My%20World&region=plot_1").code());
            Assertions.assertEquals(200,
                    client.get("/v1/region?world=My+World&region=plot_1").code());
        });
    }

    @Test
    void resolvesAWorldNameContainingALiteralPlusSign() {
        RealtyRestServer server = TestServers.withRegionInWorldNamed("My+World");
        JavalinTest.test(server.javalin(), (jsonServer, client) ->
                Assertions.assertEquals(200,
                        client.get("/v1/region?world=My%2BWorld&region=plot_1").code()));
    }

    @Test
    void resolvesAWorldNameContainingAPercentEncodedSpace() {
        RealtyRestServer server = TestServers.withRegionInWorldNamed("100%20");
        JavalinTest.test(server.javalin(), (jsonServer, client) ->
                Assertions.assertEquals(200,
                        client.get("/v1/region?world=100%2520&region=plot_1").code()));
    }

    @Test
    void returns404ForAnUnknownWorldName() {
        RealtyRestServer server = TestServers.withNoWorlds();
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/region?world=nope&region=plot_1");
            Assertions.assertEquals(404, response.code());
            Assertions.assertTrue(response.body().string().contains("WORLD_NOT_FOUND"));
        });
    }

    @Test
    void returns404ForAnUnknownRegion() {
        RealtyRestServer server = TestServers.withWorldsButNoRegions();
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/region?world=world&region=nope");
            Assertions.assertEquals(404, response.code());
            Assertions.assertTrue(response.body().string().contains("REGION_NOT_FOUND"));
        });
    }

    @Test
    void returns400WhenTheRegionParameterIsMissing() {
        RealtyRestServer server = TestServers.withWorlds();
        JavalinTest.test(server.javalin(), (jsonServer, client) ->
                Assertions.assertEquals(400, client.get("/v1/region?world=world").code()));
    }

    @Test
    void playerIdentitiesCarryANullNameUntilEnrichmentShips() {
        RealtyRestServer server = TestServers.withForSaleRegion();
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            String body = client.get("/v1/region?world=world&region=downtown_plot_14")
                    .body().string();
            Assertions.assertTrue(body.contains("\"name\":null"));
        });
    }
}
