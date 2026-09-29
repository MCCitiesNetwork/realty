package io.github.md5sha256.realty.rest;

import io.github.md5sha256.realty.rest.module.ModuleClient;
import io.javalin.testtools.JavalinTest;
import io.javalin.testtools.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class PlayerRegionsEndpointTest {

    private static final String UUID_PARAM = "3a1c88f0-0000-0000-0000-000000000001";

    /**
     * UUID-shaped (36 chars, hyphens at 8/13/18/23) but not a valid UUID. A caller who sends
     * that shape meant a UUID, and no player name can take it, so it is a malformed UUID
     * (400 MALFORMED_UUID) rather than a name to look up, unlike a plain player name such
     * as "Notch" (502 NAME_LOOKUP_UNAVAILABLE) below.
     */
    private static final String UUID_SHAPED_BUT_INVALID = "3a1c88f0-0000-0000-0000-00000000zzzz";

    @Test
    void all_hasTheFourParts() {
        RealtyRestServer server = TestServers.withPlayerHoldings();
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/players/regions?player=" + UUID_PARAM);
            Assertions.assertEquals(200, response.code());
            String body = response.body().string();
            Assertions.assertTrue(body.contains("\"owned\":[{\"worldGuardRegionId\":\"owned_plot\""), body);
            Assertions.assertTrue(body.contains("\"authority\":[{\"worldGuardRegionId\":\"authority_plot\""), body);
            Assertions.assertTrue(body.contains("\"landlord\":[{\"worldGuardRegionId\":\"let_plot\""), body);
            Assertions.assertTrue(body.contains("\"rented\":[{\"worldGuardRegionId\":\"rented_plot\""), body);
            Assertions.assertTrue(body.contains("\"totalCount\":4"), body);
        });
    }

    @Test
    void all_aPageFilledBeforeTheRentedPart_listsNoRentedRegion() {
        // One owned, one authority and one let region fill a page of three, so the shared
        // offset leaves no room for the rented region the database would offer.
        RealtyRestServer server = TestServers.withPlayerHoldings();
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            String body = client.get("/v1/players/regions?player=" + UUID_PARAM + "&pageSize=3").body().string();
            Assertions.assertTrue(body.contains("\"let_plot\""), body);
            Assertions.assertTrue(body.contains("\"rented\":[]"), body);
        });
    }

    @Test
    void categoryAuthority_listsAuthorityLand() {
        RealtyRestServer server = TestServers.withPlayerHoldings();
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/players/regions?player=" + UUID_PARAM + "&category=authority");
            Assertions.assertEquals(200, response.code());
            String body = response.body().string();
            Assertions.assertTrue(body.contains("\"regions\":[{\"worldGuardRegionId\":\"authority_plot\""), body);
            Assertions.assertTrue(body.contains("\"totalCount\":1"), body);
            Assertions.assertFalse(body.contains("let_plot"), body);
        });
    }

    @Test
    void categoryLandlord_listsLetLand() {
        RealtyRestServer server = TestServers.withPlayerHoldings();
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/players/regions?player=" + UUID_PARAM + "&category=landlord");
            Assertions.assertEquals(200, response.code());
            String body = response.body().string();
            Assertions.assertTrue(body.contains("\"regions\":[{\"worldGuardRegionId\":\"let_plot\""), body);
            Assertions.assertTrue(body.contains("\"totalCount\":1"), body);
            Assertions.assertFalse(body.contains("authority_plot"), body);
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

    @Test
    void categoryAllOmitsTheSingleCategoryRegionsField() {
        RealtyRestServer server = TestServers.withPlayerHoldings();
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            String body = client.get("/v1/players/regions?player=" + UUID_PARAM + "&category=all")
                    .body().string();
            Assertions.assertTrue(body.contains("\"owned\""));
            Assertions.assertTrue(body.contains("\"authority\""));
            Assertions.assertTrue(body.contains("\"landlord\""));
            Assertions.assertTrue(body.contains("\"rented\""));
            Assertions.assertFalse(body.contains("\"regions\""));
        });
    }

    @Test
    void categoryOwnedOmitsTheFourCategoryFields() {
        RealtyRestServer server = TestServers.withPlayerHoldings();
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            String body = client.get("/v1/players/regions?player=" + UUID_PARAM + "&category=owned")
                    .body().string();
            Assertions.assertTrue(body.contains("\"regions\""));
            Assertions.assertFalse(body.contains("\"owned\""));
            Assertions.assertFalse(body.contains("\"authority\""));
            Assertions.assertFalse(body.contains("\"landlord\""));
            Assertions.assertFalse(body.contains("\"rented\""));
        });
    }

    @Test
    void aRegionInAWorldMissingFromTheTableStillAppearsWithANullWorldName() {
        RealtyRestServer server = TestServers.withPlayerOwningRegionInMissingWorld();
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/players/regions?player=" + UUID_PARAM + "&category=owned");
            Assertions.assertEquals(200, response.code());
            String body = response.body().string();
            Assertions.assertTrue(body.contains("\"orphaned_plot\""));
            Assertions.assertTrue(body.contains("\"name\":null"));
        });
    }

    @Test
    void unknownCategory_is400() {
        RealtyRestServer server = TestServers.withPlayerHoldings();
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            for (String category : List.of("owned2", "authorities", "Landlord", "tenant")) {
                Response response = client.get("/v1/players/regions?player=" + UUID_PARAM + "&category=" + category);
                Assertions.assertEquals(400, response.code(), category);
                String body = response.body().string();
                Assertions.assertTrue(body.contains("INVALID_CATEGORY"), body);
                Assertions.assertFalse(body.contains(category), body);
            }
        });
    }

    @Test
    void returns400ForAPageSoLargeItsOffsetOverflows() {
        RealtyRestServer server = TestServers.withPlayerHoldings();
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/players/regions?player=" + UUID_PARAM + "&page=2147483647");
            Assertions.assertEquals(400, response.code());
            Assertions.assertTrue(response.body().string().contains("INVALID_PAGE"));
        });
    }

    @Test
    void categoryRentedOmitsTheFourCategoryFields() {
        RealtyRestServer server = TestServers.withPlayerHoldings();
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            String body = client.get("/v1/players/regions?player=" + UUID_PARAM + "&category=rented")
                    .body().string();
            Assertions.assertTrue(body.contains("\"regions\""));
            Assertions.assertFalse(body.contains("\"owned\""));
            Assertions.assertFalse(body.contains("\"authority\""));
            Assertions.assertFalse(body.contains("\"landlord\""));
            Assertions.assertFalse(body.contains("\"rented\""));
        });
    }

    @Test
    void resolvesAPlayerNameThroughTheModuleAndNamesTheRef() {
        ModuleClient module = TestServers.stubModule(
                Map.of(TestServers.PLAYER_ID, ".Cool Guy 123"), Map.of(), Map.of(".Cool Guy 123", TestServers.PLAYER_ID));
        JavalinTest.test(TestServers.withPlayerHoldingsAndModule(module).javalin(), (server, client) -> {
            for (String encoded : List.of(".Cool%20Guy%20123", ".Cool+Guy+123")) {
                Response response = client.get("/v1/players/regions?player=" + encoded);
                Assertions.assertEquals(200, response.code(), encoded);
                Assertions.assertTrue(response.body().string().contains(
                        "\"player\":{\"kind\":\"personal\",\"id\":\"" + TestServers.PLAYER_ID + "\",\"name\":\".Cool Guy 123\"}"), encoded);
            }
        });
    }

    @Test
    void anUnknownNameIs404() {
        ModuleClient module = TestServers.stubModule(Map.of(), Map.of(), Map.of());
        JavalinTest.test(TestServers.withPlayerHoldingsAndModule(module).javalin(), (server, client) -> {
            Response response = client.get("/v1/players/regions?player=nobody");
            Assertions.assertEquals(404, response.code());
            Assertions.assertTrue(response.body().string().contains("PLAYER_NOT_FOUND"));
        });
    }

    @Test
    void aNameWithAnUnreachableModuleIs502ButAUuidStillWorks() {
        JavalinTest.test(TestServers.withPlayerHoldingsAndModule(TestServers.unreachableModule()).javalin(), (server, client) -> {
            Assertions.assertEquals(502, client.get("/v1/players/regions?player=Notch").code());
            Response byId = client.get("/v1/players/regions?player=" + TestServers.PLAYER_ID);
            Assertions.assertEquals(200, byId.code());
            Assertions.assertTrue(byId.body().string().contains("\"name\":null"));
        });
    }
}
