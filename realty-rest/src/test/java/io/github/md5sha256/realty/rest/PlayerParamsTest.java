package io.github.md5sha256.realty.rest;

import io.javalin.testtools.JavalinTest;
import okhttp3.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

/**
 * The {@code playerId} / {@code playerName} contract, shared by every route that
 * identifies a player.
 *
 * <p>The two are separate parameters rather than one polymorphic {@code player}
 * because they do not cost the same thing. {@code playerId} is answered from the
 * database alone; {@code playerName} needs the query-service module, and so can fail
 * in ways {@code playerId} cannot. One parameter carrying both meant a caller could
 * not tell from the request whether the module was on the critical path.</p>
 */
class PlayerParamsTest {

    private static final UUID PLAYER = UUID.fromString("3a1c88f0-0000-0000-0000-000000000099");

    @Test
    void resolvesAPlayerId() {
        RealtyRestServer server = TestServers.withPlayerSummary(Map.of(), Map.of(PLAYER, "Notch"));
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/players/summary?playerId=" + PLAYER);
            Assertions.assertEquals(200, response.code());
            Assertions.assertTrue(response.body().string().contains(PLAYER.toString()));
        });
    }

    @Test
    void resolvesAPlayerName() {
        RealtyRestServer server = TestServers.withPlayerSummaryByName("Notch", PLAYER);
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/players/summary?playerName=Notch");
            Assertions.assertEquals(200, response.code());
            Assertions.assertTrue(response.body().string().contains(PLAYER.toString()));
        });
    }

    @Test
    void rejectsBothParametersAtOnce() {
        RealtyRestServer server = TestServers.withPlayerSummary(Map.of(), Map.of());
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/players/summary?playerId=" + PLAYER + "&playerName=Notch");
            Assertions.assertEquals(400, response.code());
            Assertions.assertTrue(response.body().string().contains("AMBIGUOUS_PARAMETER"),
                    "giving both is a caller mistake, not a silent precedence rule");
        });
    }

    @Test
    void rejectsNeitherParameterWhereOneIsRequired() {
        RealtyRestServer server = TestServers.withPlayerSummary(Map.of(), Map.of());
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/players/summary");
            Assertions.assertEquals(400, response.code());
            Assertions.assertTrue(response.body().string().contains("MISSING_PARAMETER"));
        });
    }

    @Test
    void noLongerAcceptsTheOldPolymorphicPlayerParameter() {
        RealtyRestServer server = TestServers.withPlayerSummary(Map.of(), Map.of());
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/players/summary?player=" + PLAYER);
            Assertions.assertEquals(400, response.code(),
                    "player= is gone; a caller still sending it should be told, not silently ignored");
            Assertions.assertTrue(response.body().string().contains("MISSING_PARAMETER"));
        });
    }

    @Test
    void rejectsAMalformedPlayerId() {
        RealtyRestServer server = TestServers.withPlayerSummary(Map.of(), Map.of());
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/players/summary?playerId=not-a-uuid");
            Assertions.assertEquals(400, response.code());
            Assertions.assertTrue(response.body().string().contains("MALFORMED_UUID"),
                    "playerId is typed, so a name-shaped value there is an error, not a name");
        });
    }

    @Test
    void answersAPlayerIdWhileTheModuleIsUnreachable() {
        RealtyRestServer server = TestServers.withPlayerSummaryAndModule(TestServers.unreachableModule());
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/players/summary?playerId=" + PLAYER);
            Assertions.assertEquals(200, response.code(),
                    "playerId needs only the database; the module going down must not fail it");
            Assertions.assertTrue(response.body().string().contains("\"name\":null"),
                    "the name degrades to null, like every other module-sourced field");
        });
    }

    @Test
    void reportsAPlayerNameAsUnavailableWhileTheModuleIsUnreachable() {
        RealtyRestServer server = TestServers.withPlayerSummaryAndModule(TestServers.unreachableModule());
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/players/summary?playerName=Notch");
            Assertions.assertEquals(502, response.code(),
                    "playerName has nothing to degrade to, so it is the one that fails");
            Assertions.assertTrue(response.body().string().contains("NAME_LOOKUP_UNAVAILABLE"));
        });
    }

    @Test
    void appliesTheSameRuleToTheHistoryFilter() {
        RealtyRestServer server = TestServers.withHistory(java.util.List.of(), 0, Map.of());
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            String url = "/v1/region/history?world=world&region=downtown_plot_14";
            Assertions.assertEquals(200, client.get(url).code(),
                    "the history filter is optional, so neither parameter is fine");
            Response both = client.get(url + "&playerId=" + PLAYER + "&playerName=Notch");
            Assertions.assertEquals(400, both.code());
            Assertions.assertTrue(both.body().string().contains("AMBIGUOUS_PARAMETER"));
        });
    }
}
