package io.github.md5sha256.realty.adapter.query;

import io.javalin.testtools.JavalinTest;
import io.javalin.testtools.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class AuthenticationTest {

    private static final String PATH = "/regions/" + TestServers.WORLD + "/downtown_plot_14/dimensions";

    @Test
    void aMissingSecretIs401() {
        JavalinTest.test(TestServers.standard().javalin(), (server, client) -> {
            Response response = client.get(PATH);
            Assertions.assertEquals(401, response.code());
            Assertions.assertTrue(response.body().string().contains("\"error\":\"UNAUTHORIZED\""));
        });
    }

    @Test
    void aWrongSecretIs401() {
        JavalinTest.test(TestServers.standard().javalin(), (server, client) -> {
            Response response = client.get(PATH, req -> req.header(QueryServiceServer.SECRET_HEADER, "nope"));
            Assertions.assertEquals(401, response.code());
        });
    }

    @Test
    void anUnknownRouteStillRequiresTheSecret() {
        JavalinTest.test(TestServers.standard().javalin(), (server, client) -> {
            Assertions.assertEquals(401, client.get("/nothing-here").code());
        });
    }

    @Test
    void healthAnswersWithTheSecret() {
        JavalinTest.test(TestServers.standard().javalin(), (server, client) -> {
            Response response = client.get("/health",
                    req -> req.header(QueryServiceServer.SECRET_HEADER, TestServers.SECRET));
            Assertions.assertEquals(200, response.code());
            Assertions.assertTrue(response.body().string().contains("\"status\":\"ok\""));
        });
    }
}
