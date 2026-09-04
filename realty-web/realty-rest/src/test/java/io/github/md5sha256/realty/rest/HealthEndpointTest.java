package io.github.md5sha256.realty.rest;

import io.github.md5sha256.realty.rest.module.ModuleClient;
import io.javalin.testtools.JavalinTest;
import io.javalin.testtools.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

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
    void reportsTheModuleAsDisabledWhenNoUrlIsConfigured() {
        JavalinTest.test(TestServers.withHealthyDatabase().javalin(), (server, client) -> {
            Response response = client.get("/v1/health");
            Assertions.assertEquals(200, response.code());
            Assertions.assertEquals("{\"status\":\"ok\",\"module\":\"disabled\"}", response.body().string());
        });
    }

    @Test
    void anUnreachableModuleIsDegradedNotUnhealthy() {
        JavalinTest.test(TestServers.withModule(TestServers.unreachableModule()).javalin(), (server, client) -> {
            Response response = client.get("/v1/health");
            Assertions.assertEquals(200, response.code());
            Assertions.assertEquals("{\"status\":\"ok\",\"module\":\"unreachable\"}", response.body().string());
        });
    }

    @Test
    void aReachableModuleReportsOk() {
        ModuleClient stub = TestServers.stubModule(Map.of(), Map.of(), Map.of());
        JavalinTest.test(TestServers.withModule(stub).javalin(), (server, client) -> {
            Assertions.assertEquals("{\"status\":\"ok\",\"module\":\"ok\"}", client.get("/v1/health").body().string());
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
