package io.github.md5sha256.realty.rest;

import io.javalin.testtools.JavalinTest;
import io.javalin.testtools.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

class StaticSiteTest {

    @TempDir
    Path webRoot;

    @BeforeEach
    void writeIndex() throws IOException {
        Files.writeString(this.webRoot.resolve("index.html"), "<html>explorer</html>");
    }

    @Test
    void servesIndexAtTheRoot() {
        JavalinTest.test(TestServers.withStaticSite(this.webRoot).javalin(), (app, client) -> {
            Response response = client.get("/");
            Assertions.assertEquals(200, response.code());
            Assertions.assertTrue(response.body().string().contains("explorer"));
        });
    }

    @Test
    void servesIndexForAClientSideRoute() {
        // A deep link into the SPA must not 404: the router runs in the browser.
        JavalinTest.test(TestServers.withStaticSite(this.webRoot).javalin(), (app, client) -> {
            Response response = client.get("/region/plot_a");
            Assertions.assertEquals(200, response.code());
            Assertions.assertTrue(response.body().string().contains("explorer"));
        });
    }

    @Test
    void anUnknownApiPathStillReturnsJsonNotIndexHtml() {
        // The trap this whole seam has to avoid: spaRoot catches every unmatched
        // GET, so without skipFileFunction an API client asking for a bad endpoint
        // gets index.html and a 200.
        JavalinTest.test(TestServers.withStaticSite(this.webRoot).javalin(), (app, client) -> {
            Response response = client.get("/v1/nope");
            Assertions.assertEquals(404, response.code());
            String body = response.body().string();
            Assertions.assertTrue(body.contains("\"error\""), "expected a JSON error, got: " + body);
            Assertions.assertFalse(body.contains("explorer"), "index.html was served for an API path");
        });
    }

    @Test
    void aRealApiRouteStillWorks() {
        JavalinTest.test(TestServers.withStaticSite(this.webRoot).javalin(), (app, client) -> {
            Assertions.assertEquals(200, client.get("/v1/health").code());
        });
    }

    @Test
    void withoutAStaticSiteTheRootIs404() {
        // The default must stay a pure API.
        JavalinTest.test(TestServers.withHealthyDatabase().javalin(), (app, client) -> {
            Assertions.assertEquals(404, client.get("/").code());
        });
    }
}
