package io.github.md5sha256.realty.adapter.query;

import io.javalin.testtools.JavalinTest;
import io.javalin.testtools.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;

class DimensionsEndpointTest {

    private static io.javalin.testtools.Request.Builder auth(io.javalin.testtools.Request.Builder req) {
        return req.header(QueryServiceServer.SECRET_HEADER, TestServers.SECRET);
    }

    @Test
    void returnsShapeBoundsAndPoints() {
        JavalinTest.test(TestServers.standard().javalin(), (server, client) -> {
            Response response = client.get(
                    "/regions/" + TestServers.WORLD + "/downtown_plot_14/dimensions",
                    DimensionsEndpointTest::auth);
            Assertions.assertEquals(200, response.code());
            String body = response.body().string();
            Assertions.assertEquals(
                    "{\"shape\":\"POLYGONAL\",\"minY\":62,\"maxY\":140,\"points\":["
                            + "{\"x\":104,\"z\":-88},{\"x\":131,\"z\":-88},"
                            + "{\"x\":131,\"z\":-61},{\"x\":104,\"z\":-61}]}",
                    body);
        });
    }

    @Test
    void anUnknownRegionIs404() {
        JavalinTest.test(TestServers.standard().javalin(), (server, client) -> {
            Response response = client.get(
                    "/regions/" + TestServers.WORLD + "/plot_9/dimensions", DimensionsEndpointTest::auth);
            Assertions.assertEquals(404, response.code());
            Assertions.assertTrue(response.body().string().contains("\"error\":\"REGION_NOT_FOUND\""));
        });
    }

    @Test
    void aMalformedWorldIdIs400() {
        JavalinTest.test(TestServers.standard().javalin(), (server, client) -> {
            Response response = client.get("/regions/not-a-uuid/plot/dimensions", DimensionsEndpointTest::auth);
            Assertions.assertEquals(400, response.code());
            Assertions.assertTrue(response.body().string().contains("\"error\":\"INVALID_WORLD_ID\""));
        });
    }

    @Test
    void aStalledMainThreadIs504NotAHang() {
        QueryServiceServer server = TestServers.withStalledMainThread(Duration.ofMillis(200));
        JavalinTest.test(server.javalin(), (s, client) -> {
            long started = System.nanoTime();
            Response response = client.get(
                    "/regions/" + TestServers.WORLD + "/downtown_plot_14/dimensions",
                    DimensionsEndpointTest::auth);
            long elapsedMs = (System.nanoTime() - started) / 1_000_000;
            Assertions.assertEquals(504, response.code());
            Assertions.assertTrue(response.body().string().contains("\"error\":\"MAIN_THREAD_TIMEOUT\""));
            Assertions.assertTrue(elapsedMs < 5_000, "took " + elapsedMs + "ms");
        });
    }
}
