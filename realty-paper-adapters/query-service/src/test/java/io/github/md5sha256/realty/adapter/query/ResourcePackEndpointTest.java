package io.github.md5sha256.realty.adapter.query;

import io.javalin.testtools.JavalinTest;
import io.javalin.testtools.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ResourcePackEndpointTest {

    private static io.javalin.testtools.Request.Builder auth(io.javalin.testtools.Request.Builder req) {
        return req.header(QueryServiceServer.SECRET_HEADER, TestServers.SECRET);
    }

    @Test
    void reportsTheConfiguredPack() {
        QueryServiceServer server = TestServers.withResourcePack(
                "https://cdn.example.com/pack.zip", "abc123", true);
        JavalinTest.test(server.javalin(), (app, client) -> {
            Response response = client.get("/resource-pack", ResourcePackEndpointTest::auth);
            Assertions.assertEquals(200, response.code());
            String body = response.body().string();
            Assertions.assertTrue(body.contains("https://cdn.example.com/pack.zip"), body);
            Assertions.assertTrue(body.contains("abc123"), body);
            Assertions.assertTrue(body.contains("\"required\":true"), body);
        });
    }

    @Test
    void reportsANullUrlWhenNoPackIsConfigured() {
        // The default in server.properties is an empty resource-pack, so this is the
        // common case, not an error.
        QueryServiceServer server = TestServers.withoutResourcePack();
        JavalinTest.test(server.javalin(), (app, client) -> {
            Response response = client.get("/resource-pack", ResourcePackEndpointTest::auth);
            Assertions.assertEquals(200, response.code());
            Assertions.assertTrue(response.body().string().contains("\"url\":null"));
        });
    }

    @Test
    void requiresTheSharedSecretLikeEveryOtherRoute() {
        QueryServiceServer server = TestServers.withoutResourcePack();
        JavalinTest.test(server.javalin(), (app, client) ->
                Assertions.assertEquals(401, client.get("/resource-pack").code()));
    }
}
