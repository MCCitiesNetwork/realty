package io.github.md5sha256.realty.rest;

import io.javalin.testtools.JavalinTest;
import io.javalin.testtools.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

class StaticSiteTest {

    @TempDir
    Path webRoot;

    /** Stands in for the working directory a bundled deployment runs from. */
    @TempDir
    Path beside;

    @BeforeEach
    void writeIndex() throws IOException {
        Files.writeString(this.webRoot.resolve("index.html"), "<html>explorer</html>");
        Files.writeString(this.webRoot.resolve("config.json"), "{\"apiBaseUrl\":\"\"}");
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
    void anUnknownApiPathIs404ForHeadToo() {
        // A client that only wants the status uses HEAD, and Jetty answers HEAD from
        // the static handler unless a route claims the path -- so this returned 200
        // while the GET above returned 404, which is worse than either alone.
        // javalin-testtools cannot issue HEAD, so this goes through java.net.http
        // against the same started server.
        JavalinTest.test(TestServers.withStaticSite(this.webRoot).javalin(), (app, client) -> {
            HttpRequest request = HttpRequest.newBuilder(URI.create(client.getOrigin() + "/v1/nope"))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<Void> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.discarding());
            Assertions.assertEquals(404, response.statusCode());
        });
    }

    @Test
    void aRealApiRouteStillWorks() {
        JavalinTest.test(TestServers.withStaticSite(this.webRoot).javalin(), (app, client) -> {
            Assertions.assertEquals(200, client.get("/v1/health").code());
        });
    }

    @Test
    void aFrontEndsOwnConfigJsonIsServedFromDisk() {
        // A split deployment ships a real config.json beside index.html; nothing about it
        // is synthesised here, which is why the bundled build needs none.
        JavalinTest.test(TestServers.withStaticSite(this.webRoot).javalin(), (app, client) -> {
            Response response = client.get("/config.json");
            Assertions.assertEquals(200, response.code());
            Assertions.assertTrue(response.body().string().contains("apiBaseUrl"));
        });
    }

    @Test
    void aConfigOnDiskIsServedInPlaceOfThePackagedOne() throws IOException {
        // The bundled jar is built once and installed everywhere, so the config.json it
        // carries cannot be this deployment's. One beside the jar is.
        Path beside = this.beside.resolve("config.json");
        Files.writeString(beside, "{\"currency\":\"$\"}");
        JavalinTest.test(TestServers.withStaticSite(this.webRoot, beside).javalin(), (app, client) -> {
            Response response = client.get("/config.json");
            Assertions.assertEquals(200, response.code());
            Assertions.assertEquals("{\"currency\":\"$\"}", response.body().string(),
                    "the packaged config.json was served in place of the operator's");
            Assertions.assertTrue(response.headers().get("Content-Type").contains("application/json"),
                    "a browser must read it as JSON, not as a download");
        });
    }

    @Test
    void withNoConfigOnDiskThePackagedOneIsStillServed() {
        // Naming a path is not the same as having a file there. A deployment without one
        // behaves exactly as it did before the override existed.
        JavalinTest.test(TestServers.withStaticSite(this.webRoot, this.beside.resolve("config.json"))
                .javalin(), (app, client) -> {
            Response response = client.get("/config.json");
            Assertions.assertEquals(200, response.code());
            Assertions.assertTrue(response.body().string().contains("apiBaseUrl"));
        });
    }

    @Test
    void theOverrideExposesNothingButTheConfig() throws IOException {
        // The file sits in the working directory, which on a panel holds the jar and
        // whatever else the operator keeps there. Exactly one path is served from it.
        Path beside = this.beside.resolve("config.json");
        Files.writeString(beside, "{}");
        Files.writeString(this.beside.resolve("secrets.env"), "REALTY_DB_PASSWORD=hunter2");
        JavalinTest.test(TestServers.withStaticSite(this.webRoot, beside).javalin(), (app, client) -> {
            String body = client.get("/secrets.env").body().string();
            Assertions.assertFalse(body.contains("hunter2"), "a sibling file was reachable");
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
