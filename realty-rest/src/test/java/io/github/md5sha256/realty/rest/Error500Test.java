package io.github.md5sha256.realty.rest;

import io.javalin.testtools.JavalinTest;
import okhttp3.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Pins {@code RealtyRestServer}'s catch-all 500 handler: an unhandled exception
 * must always come back as the generic {@code INTERNAL_ERROR} body, never the
 * exception's own message.
 */
class Error500Test {

    private static final String SECRET_MESSAGE = "db password is hunter2-do-not-leak-this-9f3c";

    @Test
    void anUnhandledExceptionReturns500WithoutLeakingItsMessage() throws IOException {
        RealtyRestServer server = TestServers.withWorldLookupThatThrows(SECRET_MESSAGE);
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/worlds");
            Assertions.assertEquals(500, response.code());
            String body = response.body().string();
            Assertions.assertFalse(body.contains(SECRET_MESSAGE));
            Assertions.assertTrue(body.contains("INTERNAL_ERROR"));
            Assertions.assertTrue(body.contains("An unexpected error occurred"));
        });
    }
}
