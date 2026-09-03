package io.github.md5sha256.realty.rest;

import io.javalin.testtools.JavalinTest;
import okhttp3.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

class PlayerSummaryEndpointTest {

    private static final UUID PLAYER = UUID.fromString("3a1c88f0-0000-0000-0000-000000000099");

    @Test
    void reportsEveryHoldingCounter() {
        RealtyRestServer server = TestServers.withPlayerSummary(Map.of(
                "countRegionsByTitleHolder", 7,
                "countRegionsByLandlord", 3,
                "countOccupiedLeaseholdsByLandlord", 2,
                "countRegionsByTenant", 1,
                "countRegionsByAuthority", 0), Map.of());
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/players/summary?player=" + PLAYER);
            Assertions.assertEquals(200, response.code());
            String body = response.body().string();
            Assertions.assertTrue(body.contains("\"titleHeld\":7"), body);
            Assertions.assertTrue(body.contains("\"landlordOf\":3"), body);
            Assertions.assertTrue(body.contains("\"occupiedLandlordOf\":2"), body);
            Assertions.assertTrue(body.contains("\"renting\":1"), body);
            Assertions.assertTrue(body.contains("\"authorityOver\":0"), body);
        });
    }

    @Test
    void carriesThePlayerIdentityAlongsideTheCounters() {
        RealtyRestServer server = TestServers.withPlayerSummary(Map.of(), Map.of(PLAYER, "Notch"));
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/players/summary?player=" + PLAYER);
            String body = response.body().string();
            Assertions.assertTrue(body.contains("\"id\":\"" + PLAYER + "\""), body);
            Assertions.assertTrue(body.contains("\"name\":\"Notch\""), body);
        });
    }

    @Test
    void acceptsAPlayerNameAsWellAsAUuid() {
        RealtyRestServer server = TestServers.withPlayerSummaryByName("Notch", PLAYER);
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/players/summary?player=Notch");
            Assertions.assertEquals(200, response.code());
            Assertions.assertTrue(response.body().string().contains(PLAYER.toString()));
        });
    }

    @Test
    void rejectsAMissingPlayerParameter() {
        RealtyRestServer server = TestServers.withPlayerSummary(Map.of(), Map.of());
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/players/summary");
            Assertions.assertEquals(400, response.code());
            Assertions.assertTrue(response.body().string().contains("MISSING_PARAMETER"));
        });
    }

    @Test
    void rejectsAMalformedUuid() {
        RealtyRestServer server = TestServers.withPlayerSummary(Map.of(), Map.of());
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/players/summary?player=zzzzzzzz-0000-0000-0000-000000000099");
            Assertions.assertEquals(400, response.code());
            Assertions.assertTrue(response.body().string().contains("MALFORMED_UUID"));
        });
    }

    @Test
    void reportsZeroesForAPlayerHoldingNothingRatherThan404() {
        RealtyRestServer server = TestServers.withPlayerSummary(Map.of(
                "countRegionsByTitleHolder", 0,
                "countRegionsByLandlord", 0,
                "countOccupiedLeaseholdsByLandlord", 0,
                "countRegionsByTenant", 0,
                "countRegionsByAuthority", 0), Map.of());
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/players/summary?player=" + PLAYER);
            Assertions.assertEquals(200, response.code(),
                    "a player who owns nothing is a valid answer, not a missing resource");
            Assertions.assertTrue(response.body().string().contains("\"titleHeld\":0"));
        });
    }
}
