package io.github.md5sha256.realty.rest;

import io.javalin.testtools.JavalinTest;
import okhttp3.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

class HealthEndpointTest {

    @Test
    void healthReturnsOkWhenTheDatabaseAnswers() throws IOException {
        RealtyRestServer server = TestServers.withHealthyDatabase();
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/health");
            Assertions.assertEquals(200, response.code());
            Assertions.assertTrue(response.body().string().contains("\"status\":\"ok\""));
        });
    }

    @Test
    void healthReturns503WhenTheDatabaseIsUnreachable() {
        RealtyRestServer server = TestServers.withFailingDatabase();
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/health");
            Assertions.assertEquals(503, response.code());
        });
    }

    @Test
    void anUnknownPathReturnsAJsonErrorBody() {
        RealtyRestServer server = TestServers.withHealthyDatabase();
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/nope");
            Assertions.assertEquals(404, response.code());
            Assertions.assertTrue(response.body().string().contains("\"error\""));
        });
    }
}
