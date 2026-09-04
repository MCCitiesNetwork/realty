package io.github.md5sha256.realty.rest;

import io.javalin.testtools.JavalinTest;
import io.javalin.testtools.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class WorldsEndpointTest {

    @Test
    void listsEveryKnownWorld() {
        RealtyRestServer server = TestServers.withWorlds();
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/worlds");
            Assertions.assertEquals(200, response.code());
            String body = response.body().string();
            Assertions.assertTrue(body.contains("\"name\":\"world\""));
            Assertions.assertTrue(body.contains("\"name\":\"My World\""));
        });
    }

    @Test
    void returnsAnEmptyArrayWhenNoWorldsAreKnown() {
        RealtyRestServer server = TestServers.withNoWorlds();
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/worlds");
            Assertions.assertEquals(200, response.code());
            Assertions.assertEquals("[]", response.body().string().trim());
        });
    }
}
