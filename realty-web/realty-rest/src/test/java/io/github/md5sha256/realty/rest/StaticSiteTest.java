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
import java.util.List;

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
    void servesTheSynthesisedConfigWhenOneIsGiven() {
        // The bundled build has no config.json on disk to edit -- it is inside the jar --
        // so realty-rest serves the document the dist entry point renders instead.
        String body = "{\"resourcePackAttribution\":[{\"text\":\"Faithful 64x\"}]}";
        JavalinTest.test(TestServers.withStaticSite(this.webRoot, body).javalin(), (app, client) -> {
            Response response = client.get("/config.json");
            Assertions.assertEquals(200, response.code());
            Assertions.assertEquals(List.of("application/json"), response.headers().get("Content-Type"));
            Assertions.assertEquals(body, response.body().string());
        });
    }

    @Test
    void withoutAConfigTheFrontEndsOwnFileIsServedIfThereIsOne() throws IOException {
        // A split deployment ships a real config.json beside index.html and passes none
        // here; the static handler must still serve the operator's file.
        Files.writeString(this.webRoot.resolve("config.json"), "{\"apiBaseUrl\":\"\"}");
        JavalinTest.test(TestServers.withStaticSite(this.webRoot).javalin(), (app, client) -> {
            Response response = client.get("/config.json");
            Assertions.assertEquals(200, response.code());
            Assertions.assertTrue(response.body().string().contains("apiBaseUrl"));
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
