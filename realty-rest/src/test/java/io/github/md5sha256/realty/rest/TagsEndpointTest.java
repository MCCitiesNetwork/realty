package io.github.md5sha256.realty.rest;

import io.javalin.testtools.JavalinTest;
import okhttp3.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class TagsEndpointTest {

    @Test
    void listsEveryTagWithItsRegionCount() {
        RealtyRestServer server = TestServers.withTags(
                List.of("commercial", "waterfront"),
                Map.of("commercial", 42, "waterfront", 7));
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/tags");
            Assertions.assertEquals(200, response.code());
            Assertions.assertEquals(
                    "[{\"id\":\"commercial\",\"regionCount\":42},"
                            + "{\"id\":\"waterfront\",\"regionCount\":7}]",
                    response.body().string().trim());
        });
    }

    @Test
    void reportsTheTagOrderTheBackendGaveRatherThanReSorting() {
        RealtyRestServer server = TestServers.withTags(
                List.of("waterfront", "commercial"),
                Map.of("commercial", 1, "waterfront", 2));
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/tags");
            String body = response.body().string();
            Assertions.assertTrue(body.indexOf("waterfront") < body.indexOf("commercial"),
                    "expected the backend's own order to survive, got: " + body);
        });
    }

    @Test
    void returnsAnEmptyArrayWhenNoTagIsInUse() {
        RealtyRestServer server = TestServers.withTags(List.of(), Map.of());
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/tags");
            Assertions.assertEquals(200, response.code());
            Assertions.assertEquals("[]", response.body().string().trim());
        });
    }
}
