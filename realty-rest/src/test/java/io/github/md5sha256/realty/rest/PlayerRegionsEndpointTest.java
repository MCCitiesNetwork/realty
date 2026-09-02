package io.github.md5sha256.realty.rest;

import io.javalin.testtools.JavalinTest;
import okhttp3.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class PlayerRegionsEndpointTest {

    private static final String UUID_PARAM = "3a1c88f0-0000-0000-0000-000000000001";

    /**
     * UUID-shaped (36 chars, hyphens at 8/13/18/23) but not a valid UUID -- per the
     * task-8 ruling this is a malformed UUID (400 MALFORMED_UUID), distinct from a
     * plain player name like "Notch" (502 NAME_LOOKUP_UNAVAILABLE) below.
     */
    private static final String UUID_SHAPED_BUT_INVALID = "3a1c88f0-0000-0000-0000-00000000zzzz";

    @Test
    void returnsThreeCategoriesForCategoryAll() {
        RealtyRestServer server = TestServers.withPlayerHoldings();
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/players/regions?player=" + UUID_PARAM);
            Assertions.assertEquals(200, response.code());
            String body = response.body().string();
            Assertions.assertTrue(body.contains("\"owned\""));
            Assertions.assertTrue(body.contains("\"landlord\""));
            Assertions.assertTrue(body.contains("\"rented\""));
        });
    }

    @Test
    void rentedEntriesCarryEndDateAndSecondsRemaining() {
        RealtyRestServer server = TestServers.withPlayerHoldings();
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            String body = client.get("/v1/players/regions?player=" + UUID_PARAM).body().string();
            Assertions.assertTrue(body.contains("\"secondsRemaining\""));
            Assertions.assertTrue(body.contains("\"endDate\""));
        });
    }

    @Test
    void aPlayerWhoOwnsNothingIs200WithZeroTotal() {
        RealtyRestServer server = TestServers.withEmptyPlayerHoldings();
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/players/regions?player=" + UUID_PARAM);
            Assertions.assertEquals(200, response.code());
            Assertions.assertTrue(response.body().string().contains("\"totalCount\":0"));
        });
    }

    @Test
    void returns400ForAUuidShapedButInvalidValue() {
        RealtyRestServer server = TestServers.withPlayerHoldings();
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/players/regions?player=" + UUID_SHAPED_BUT_INVALID);
            Assertions.assertEquals(400, response.code());
        });
    }

    @Test
    void returns502WhenLookingUpByNameWithNoModuleConfigured() {
        RealtyRestServer server = TestServers.withPlayerHoldings();
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/players/regions?player=Notch");
            Assertions.assertEquals(502, response.code());
            Assertions.assertTrue(response.body().string().contains("NAME_LOOKUP_UNAVAILABLE"));
        });
    }

    @Test
    void clampsPageSizeToTheConfiguredMaximum() {
        RealtyRestServer server = TestServers.withPlayerHoldingsAndMaxPageSize(10);
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            String body = client.get("/v1/players/regions?player=" + UUID_PARAM + "&pageSize=500")
                    .body().string();
            Assertions.assertTrue(body.contains("\"pageSize\":10"));
        });
    }

    @Test
    void returns400ForAPageBelowOne() {
        RealtyRestServer server = TestServers.withPlayerHoldings();
        JavalinTest.test(server.javalin(), (jsonServer, client) ->
                Assertions.assertEquals(400,
                        client.get("/v1/players/regions?player=" + UUID_PARAM + "&page=0").code()));
    }
}
