package io.github.md5sha256.realty.adapter.query;

import io.github.md5sha256.realty.adapter.query.json.ResourcePackAttribution;
import io.github.md5sha256.realty.adapter.query.json.ResourcePackEntry;
import io.javalin.testtools.JavalinTest;
import io.javalin.testtools.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/** What {@code /resource-pack} reports once several packs can be configured. */
class ResourcePackListEndpointTest {

    private static io.javalin.testtools.Request.Builder auth(io.javalin.testtools.Request.Builder req) {
        return req.header(QueryServiceServer.SECRET_HEADER, TestServers.SECRET);
    }

    private static final List<ResourcePackEntry> TWO_PACKS = List.of(
            new ResourcePackEntry("https://cdn.example.com/override.zip",
                    List.of(new ResourcePackAttribution("Example Pack 32x", "https://packs.example.com/"))),
            new ResourcePackEntry("https://cdn.example.com/base.zip",
                    List.of(new ResourcePackAttribution("CC BY 4.0", null))));

    @Test
    void reportsEveryPackInPriorityOrder() {
        JavalinTest.test(TestServers.withResourcePacks(TWO_PACKS).javalin(), (app, client) -> {
            String body = client.get("/resource-pack", ResourcePackListEndpointTest::auth)
                    .body().string();
            int override = body.indexOf("https://cdn.example.com/override.zip");
            int base = body.indexOf("https://cdn.example.com/base.zip");
            Assertions.assertTrue(override >= 0 && base >= 0, body);
            Assertions.assertTrue(override < base,
                    "the higher-priority pack must be reported first: " + body);
        });
    }

    @Test
    void reportsEachPacksOwnCredits() {
        JavalinTest.test(TestServers.withResourcePacks(TWO_PACKS).javalin(), (app, client) -> {
            String body = client.get("/resource-pack", ResourcePackListEndpointTest::auth)
                    .body().string();
            Assertions.assertTrue(body.contains("\"text\":\"Example Pack 32x\""), body);
            Assertions.assertTrue(body.contains("\"text\":\"CC BY 4.0\""), body);
        });
    }

    @Test
    void reportsAnEmptyListWhenNoPackIsConfigured() {
        JavalinTest.test(TestServers.withResourcePacks(List.of()).javalin(), (app, client) -> {
            String body = client.get("/resource-pack", ResourcePackListEndpointTest::auth)
                    .body().string();
            Assertions.assertTrue(body.contains("\"packs\":[]"), body);
        });
    }

}
