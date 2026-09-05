package io.github.md5sha256.realty.rest;

import io.javalin.testtools.JavalinTest;
import io.javalin.testtools.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class StatsEndpointTest {

    @Test
    void isCacheableForAMinute() {
        RealtyRestServer server = TestServers.withStats(Map.of());
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Assertions.assertEquals(List.of("public, max-age=60"),
                    client.get("/v1/stats").headers().get("Cache-Control"));
        });
    }

    @Test
    void reportsEveryCounterTheBackendTracks() {
        RealtyRestServer server = TestServers.withStats(Map.ofEntries(
                Map.entry("countAllRegions", 412),
                Map.entry("countAllFreeholdContracts", 300),
                Map.entry("countOccupiedFreeholdContracts", 214),
                Map.entry("countAllLeaseholdContracts", 112),
                Map.entry("countOccupiedLeaseholdContracts", 87),
                Map.entry("countActiveOffers", 19),
                Map.entry("countActiveAuctions", 4)));
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/stats");
            Assertions.assertEquals(200, response.code());
            String body = response.body().string();
            Assertions.assertTrue(body.contains("\"regions\":412"), body);
            Assertions.assertTrue(body.contains("\"contracts\":300"), body);
            Assertions.assertTrue(body.contains("\"occupied\":214"), body);
            Assertions.assertTrue(body.contains("\"contracts\":112"), body);
            Assertions.assertTrue(body.contains("\"occupied\":87"), body);
            Assertions.assertTrue(body.contains("\"activeOffers\":19"), body);
            Assertions.assertTrue(body.contains("\"activeAuctions\":4"), body);
        });
    }

    @Test
    void nestsTheAveragesUnderTheirContractType() {
        RealtyRestServer server = TestServers.withStats(Map.of());
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/stats");
            Assertions.assertEquals(200, response.code());
            String body = response.body().string();
            Assertions.assertTrue(body.contains("\"averagePrice\":18250.5"), body);
            Assertions.assertTrue(body.contains("\"averagePrice\":640.0"), body);
            Assertions.assertTrue(body.contains("\"averageDurationSeconds\":604800"), body);
        });
    }

    @Test
    void reportsZeroesOnAnEmptyServerRatherThanOmittingTheFields() {
        RealtyRestServer server = TestServers.withEmptyStats();
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/stats");
            Assertions.assertEquals(200, response.code());
            String body = response.body().string();
            Assertions.assertTrue(body.contains("\"regions\":0"), body);
            Assertions.assertTrue(body.contains("\"averagePrice\":0.0"), body);
            Assertions.assertTrue(body.contains("\"averageDurationSeconds\":0"), body);
        });
    }
}
