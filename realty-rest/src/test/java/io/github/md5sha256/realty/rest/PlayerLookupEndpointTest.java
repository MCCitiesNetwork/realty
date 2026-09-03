package io.github.md5sha256.realty.rest;

import io.javalin.testtools.JavalinTest;
import okhttp3.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

class PlayerLookupEndpointTest {

    private static final UUID NOTCH = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");

    @Test
    void resolvesAKnownNameToItsUuid() {
        RealtyRestServer server = TestServers.withModule(
                TestServers.stubModule(Map.of(), Map.of(), Map.of("Notch", NOTCH)));
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/players/lookup?playerName=Notch");
            Assertions.assertEquals(200, response.code());
            Assertions.assertEquals(
                    "{\"id\":\"" + NOTCH + "\",\"name\":\"Notch\"}",
                    response.body().string().trim());
        });
    }

    @Test
    void resolvesANameContainingASpace() {
        UUID id = UUID.fromString("3a1c88f0-0000-0000-0000-000000000099");
        RealtyRestServer server = TestServers.withModule(
                TestServers.stubModule(Map.of(), Map.of(), Map.of(".Xbox Gamer", id)));
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/players/lookup?playerName=.Xbox%20Gamer");
            Assertions.assertEquals(200, response.code());
            Assertions.assertTrue(response.body().string().contains(id.toString()));
        });
    }

    @Test
    void rejectsAMissingNameParameter() {
        RealtyRestServer server = TestServers.withModule(
                TestServers.stubModule(Map.of(), Map.of(), Map.of()));
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/players/lookup");
            Assertions.assertEquals(400, response.code());
            Assertions.assertTrue(response.body().string().contains("MISSING_PARAMETER"));
        });
    }

    @Test
    void reportsAnUnknownNameAsNotFound() {
        RealtyRestServer server = TestServers.withModule(
                TestServers.stubModule(Map.of(), Map.of(), Map.of()));
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/players/lookup?playerName=Nobody");
            Assertions.assertEquals(404, response.code());
            Assertions.assertTrue(response.body().string().contains("PLAYER_NOT_FOUND"));
        });
    }

    @Test
    void reportsAnUnreachableModuleAsABadGateway() {
        RealtyRestServer server = TestServers.withModule(TestServers.unreachableModule());
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/players/lookup?playerName=Notch");
            Assertions.assertEquals(502, response.code());
            Assertions.assertTrue(response.body().string().contains("NAME_LOOKUP_UNAVAILABLE"));
        });
    }
}
