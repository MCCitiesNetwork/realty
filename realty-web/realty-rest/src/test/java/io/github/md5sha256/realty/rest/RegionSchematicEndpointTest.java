package io.github.md5sha256.realty.rest;

import io.javalin.testtools.JavalinTest;
import io.javalin.testtools.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Note on what is asserted here: Javalin's test client wraps an
 * {@code HttpResponse<String>}, so the body is only reachable as text and raw bytes
 * cannot be compared through it. Byte fidelity is covered where it is actually
 * testable -- {@code RealtySchematicMapperTest} round-trips 2 MB through the column
 * -- and this suite covers what the HTTP layer itself owns: status, content type,
 * and that a megabyte payload arrives whole.
 */
class RegionSchematicEndpointTest {

    private static String firstHeader(Response response, String name) {
        List<String> values = response.headers().get(name);
        return values == null || values.isEmpty() ? null : values.getFirst();
    }

    @Test
    void carriesAnEntityTagAndAnswers304WhenTheBrowserStillHoldsTheCapture() {
        // A capture is replaced in place, so the browser must ask again each visit --
        // but it need not be sent the same megabytes again when nothing changed.
        RealtyRestServer server = TestServers.withSchematic(new byte[]{1, 2, 3});
        JavalinTest.test(server.javalin(), (app, client) -> {
            Response first = client.get("/v1/region/schematic?world=world&region=plot_a");
            String etag = firstHeader(first, "ETag");
            Assertions.assertNotNull(etag, "no ETag header");
            Assertions.assertEquals("private, no-cache", firstHeader(first, "Cache-Control"));

            Response again = client.get("/v1/region/schematic?world=world&region=plot_a",
                    request -> request.header("If-None-Match", etag));
            Assertions.assertEquals(304, again.code());
            Assertions.assertEquals("", again.body().string());
        });
    }

    @Test
    void aChangedCaptureHasADifferentEntityTag() {
        JavalinTest.test(TestServers.withSchematic(new byte[]{1, 2, 3}).javalin(), (app, client) -> {
            String before = firstHeader(client.get("/v1/region/schematic?world=world&region=plot_a"), "ETag");
            JavalinTest.test(TestServers.withSchematic(new byte[]{1, 2, 4}).javalin(), (app2, client2) -> {
                String after = firstHeader(client2.get("/v1/region/schematic?world=world&region=plot_a"), "ETag");
                Assertions.assertNotEquals(before, after);
            });
        });
    }

    @Test
    void servesTheSchematicAsAnOctetStream() {
        RealtyRestServer server = TestServers.withSchematic(new byte[]{1, 2, 3});
        JavalinTest.test(server.javalin(), (app, client) -> {
            Response response = client.get("/v1/region/schematic?world=world&region=plot_a");
            Assertions.assertEquals(200, response.code());
            String contentType = firstHeader(response, "Content-Type");
            Assertions.assertNotNull(contentType, "no Content-Type header");
            Assertions.assertTrue(contentType.startsWith("application/octet-stream"),
                    "expected an octet-stream body, got " + contentType);
        });
    }

    @Test
    void returns404WhenTheRegionHasNoSchematic() {
        RealtyRestServer server = TestServers.withSchematic(null);
        JavalinTest.test(server.javalin(), (app, client) -> {
            Response response = client.get("/v1/region/schematic?world=world&region=plot_a");
            Assertions.assertEquals(404, response.code());
            Assertions.assertTrue(response.body().string().contains("SCHEMATIC_NOT_FOUND"));
        });
    }

    @Test
    void requiresBothWorldAndRegionParams() {
        RealtyRestServer server = TestServers.withSchematic(new byte[]{1});
        JavalinTest.test(server.javalin(), (app, client) -> {
            Assertions.assertEquals(400, client.get("/v1/region/schematic?world=world").code());
            Assertions.assertEquals(400, client.get("/v1/region/schematic?region=plot_a").code());
        });
    }

    @Test
    void returns404ForAnUnknownWorld() {
        RealtyRestServer server = TestServers.withSchematic(new byte[]{1});
        JavalinTest.test(server.javalin(), (app, client) -> {
            Response response = client.get("/v1/region/schematic?world=nosuchworld&region=plot_a");
            Assertions.assertEquals(404, response.code());
            Assertions.assertTrue(response.body().string().contains("WORLD_NOT_FOUND"));
        });
    }

    @Test
    void aLargeSchematicIsServedInFull() {
        // Captures are megabytes, and a handler that truncated or mis-buffered would
        // still pass the three-byte case above. Javalin chunks a payload this size, so
        // there is no Content-Length to check; instead the payload is confined to
        // printable ASCII, which survives the test client's String decoding intact and
        // so can be compared exactly.
        byte[] large = new byte[1024 * 1024];
        for (int i = 0; i < large.length; i++) {
            large[i] = (byte) ('a' + (i % 26));
        }
        String expected = new String(large, StandardCharsets.US_ASCII);

        RealtyRestServer server = TestServers.withSchematic(large);
        JavalinTest.test(server.javalin(), (app, client) -> {
            Response response = client.get("/v1/region/schematic?world=world&region=plot_a");
            Assertions.assertEquals(200, response.code());
            String body = response.body().string();
            Assertions.assertEquals(expected.length(), body.length(), "payload was truncated");
            Assertions.assertEquals(expected, body);
        });
    }
}
