package io.github.md5sha256.realty.adapter.query;

import io.javalin.testtools.JavalinTest;
import io.javalin.testtools.Request;
import io.javalin.testtools.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Set;

class RouteSurfaceTest {

    /** The routes registered for POST; everything else in ROUTES is a GET. */
    private static final Set<String> POST_ROUTES = Set.of(
            "/players/names", "/players/uuids", "/regions/{worldId}/dimensions");

    private static Request.Builder auth(Request.Builder req) {
        return req.header(QueryServiceServer.SECRET_HEADER, TestServers.SECRET);
    }

    @Test
    void everyDeclaredRouteIsServed() {
        JavalinTest.test(TestServers.standard().javalin(), (server, client) -> {
            for (String route : QueryServiceServer.ROUTES) {
                String path = route
                        .replace("{worldId}", TestServers.WORLD.toString())
                        .replace("{regionId}", "downtown_plot_14")
                        .replace("{uuid}", TestServers.NOTCH.toString());
                Response response = POST_ROUTES.contains(route)
                        ? client.post(path, "{\"ids\":[],\"names\":[]}", RouteSurfaceTest::auth)
                        : client.get(path + (route.endsWith("/at") ? "?x=0&z=0" : ""),
                                RouteSurfaceTest::auth);
                String body = response.body().string();
                Assertions.assertFalse(body.contains("\"error\":\"NOT_FOUND\""),
                        route + " is declared in ROUTES but not registered: " + body);
                Assertions.assertNotEquals(405, response.code(), route + " registered with the wrong method");
            }
        });
    }

    @Test
    void anUndeclaredRouteFallsThroughToNotFound() {
        JavalinTest.test(TestServers.standard().javalin(), (server, client) -> {
            Response response = client.get("/regions", RouteSurfaceTest::auth);
            Assertions.assertEquals(404, response.code());
            Assertions.assertTrue(response.body().string().contains("\"error\":\"NOT_FOUND\""));
        });
    }
}
