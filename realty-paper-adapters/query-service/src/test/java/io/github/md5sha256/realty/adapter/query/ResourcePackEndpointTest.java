package io.github.md5sha256.realty.adapter.query;

import io.github.md5sha256.realty.adapter.query.json.ResourcePackAttribution;
import io.javalin.testtools.JavalinTest;
import io.javalin.testtools.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

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
    void reportsTheConfiguredCreditsAlongsideTheUrl() {
        // The credit travels with the URL so an operator states both in the one file where
        // they chose the pack, rather than in the front end's config on another host.
        QueryServiceServer server = TestServers.withResourcePackAttribution(
                "https://cdn.example.com/pack.zip",
                List.of(
                        new ResourcePackAttribution("Faithful 64x", "https://faithfulpack.net/"),
                        new ResourcePackAttribution("CC BY 4.0", null)));
        JavalinTest.test(server.javalin(), (app, client) -> {
            Response response = client.get("/resource-pack", ResourcePackEndpointTest::auth);
            Assertions.assertEquals(200, response.code());
            String body = response.body().string();
            Assertions.assertTrue(body.contains("\"text\":\"Faithful 64x\""), body);
            Assertions.assertTrue(body.contains("https://faithfulpack.net/"), body);
            Assertions.assertTrue(body.contains("\"text\":\"CC BY 4.0\""), body);
        });
    }

    @Test
    void reportsAnEmptyCreditListWhenNoneIsConfigured() {
        JavalinTest.test(TestServers.withoutResourcePack().javalin(), (app, client) -> {
            Response response = client.get("/resource-pack", ResourcePackEndpointTest::auth);
            Assertions.assertTrue(response.body().string().contains("\"attribution\":[]"));
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
