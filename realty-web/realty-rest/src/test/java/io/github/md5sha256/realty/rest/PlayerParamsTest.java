package io.github.md5sha256.realty.rest;

import io.javalin.testtools.JavalinTest;
import io.javalin.testtools.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

/**
 * The {@code player} contract, shared by every route that identifies a player.
 *
 * <p>One parameter, discriminated by shape: a UUID-shaped value is looked up by id and
 * answered from the database alone, anything else is resolved as a name through the
 * query-service module. Neither a Java Edition name (at most 16 characters) nor a
 * Floodgate name (a {@code .} prefix on an Xbox gamertag) can reach the 36-character,
 * hyphens-at-8-13-18-23 shape a UUID has, so the two are never actually ambiguous --
 * there is no request the server could misread.</p>
 */
class PlayerParamsTest {

    private static final UUID PLAYER = UUID.fromString("3a1c88f0-0000-0000-0000-000000000099");

    @Test
    void resolvesAUuidShapedPlayer() {
        RealtyRestServer server = TestServers.withPlayerSummary(Map.of(), Map.of(PLAYER, "Notch"));
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/players/summary?player=" + PLAYER);
            Assertions.assertEquals(200, response.code());
            Assertions.assertTrue(response.body().string().contains(PLAYER.toString()));
        });
    }

    @Test
    void resolvesANameShapedPlayer() {
        RealtyRestServer server = TestServers.withPlayerSummaryByName("Notch", PLAYER);
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/players/summary?player=Notch");
            Assertions.assertEquals(200, response.code());
            Assertions.assertTrue(response.body().string().contains(PLAYER.toString()));
        });
    }

    @Test
    void rejectsAnAbsentPlayerWhereOneIsRequired() {
        RealtyRestServer server = TestServers.withPlayerSummary(Map.of(), Map.of());
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/players/summary");
            Assertions.assertEquals(400, response.code());
            Assertions.assertTrue(response.body().string().contains("MISSING_PARAMETER"));
        });
    }

    @Test
    void noLongerAcceptsTheSplitPlayerIdParameter() {
        RealtyRestServer server = TestServers.withPlayerSummary(Map.of(), Map.of());
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/players/summary?playerId=" + PLAYER);
            Assertions.assertEquals(400, response.code(),
                    "playerId is gone; a caller still sending it should be told, not silently ignored");
            Assertions.assertTrue(response.body().string().contains("MISSING_PARAMETER"));
        });
    }

    @Test
    void noLongerAcceptsTheSplitPlayerNameParameter() {
        RealtyRestServer server = TestServers.withPlayerSummary(Map.of(), Map.of());
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/players/summary?playerName=Notch");
            Assertions.assertEquals(400, response.code());
            Assertions.assertTrue(response.body().string().contains("MISSING_PARAMETER"));
        });
    }

    @Test
    void rejectsAMalformedUuidShapedPlayer() {
        RealtyRestServer server = TestServers.withPlayerSummary(Map.of(), Map.of());
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            // 36 characters with hyphens at 8, 13, 18, 23, but not valid hex -- shaped like a
            // UUID, so it is a malformed UUID rather than a name to look up.
            Response response = client.get("/v1/players/summary?player=zzzzzzzz-zzzz-zzzz-zzzz-zzzzzzzzzzzz");
            Assertions.assertEquals(400, response.code());
            Assertions.assertTrue(response.body().string().contains("MALFORMED_UUID"),
                    "a UUID-shaped value is typed, so a value that fails to parse is an error, "
                            + "not a name to look up");
        });
    }

    @Test
    void answersAUuidShapedPlayerWhileTheModuleIsUnreachable() {
        RealtyRestServer server = TestServers.withPlayerSummaryAndModule(TestServers.unreachableModule());
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/players/summary?player=" + PLAYER);
            Assertions.assertEquals(200, response.code(),
                    "a UUID needs only the database; the module going down must not fail it");
            Assertions.assertTrue(response.body().string().contains("\"name\":null"),
                    "the name degrades to null, like every other module-sourced field");
        });
    }

    @Test
    void reportsANameShapedPlayerAsUnavailableWhileTheModuleIsUnreachable() {
        RealtyRestServer server = TestServers.withPlayerSummaryAndModule(TestServers.unreachableModule());
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/players/summary?player=Notch");
            Assertions.assertEquals(502, response.code(),
                    "a name has nothing to degrade to, so it is the one that fails");
            Assertions.assertTrue(response.body().string().contains("NAME_LOOKUP_UNAVAILABLE"));
        });
    }

    @Test
    void appliesTheSameRuleToTheHistoryFilter() {
        RealtyRestServer server = TestServers.withHistory(java.util.List.of(), 0, Map.of());
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            String url = "/v1/region/history?world=world&region=downtown_plot_14";
            Assertions.assertEquals(200, client.get(url).code(),
                    "the history filter is optional, so an absent player is fine");
            Response byId = client.get(url + "&player=" + PLAYER);
            Assertions.assertEquals(200, byId.code());
        });
    }
}
