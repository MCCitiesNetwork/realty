package io.github.md5sha256.realty.adapter.query;

import io.javalin.testtools.JavalinTest;
import io.javalin.testtools.Request;
import io.javalin.testtools.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

class PlayerNamesEndpointTest {

    private static Request.Builder auth(Request.Builder req) {
        return req.header(QueryServiceServer.SECRET_HEADER, TestServers.SECRET);
    }

    @Test
    void singleLookupResolvesAJavaEditionName() {
        JavalinTest.test(TestServers.standard().javalin(), (server, client) -> {
            Response response = client.get("/players/" + TestServers.NOTCH + "/name",
                    PlayerNamesEndpointTest::auth);
            Assertions.assertEquals(200, response.code());
            Assertions.assertEquals(
                    "{\"id\":\"" + TestServers.NOTCH + "\",\"name\":\"Notch\"}", response.body().string());
        });
    }

    @Test
    void singleLookupOfAnUnknownUuidIsNullNameNot404() {
        UUID unknown = UUID.randomUUID();
        JavalinTest.test(TestServers.standard().javalin(), (server, client) -> {
            Response response = client.get("/players/" + unknown + "/name", PlayerNamesEndpointTest::auth);
            Assertions.assertEquals(200, response.code());
            Assertions.assertEquals("{\"id\":\"" + unknown + "\",\"name\":null}", response.body().string());
        });
    }

    @Test
    void singleLookupWithAMalformedUuidIs400() {
        JavalinTest.test(TestServers.standard().javalin(), (server, client) -> {
            Response response = client.get("/players/steve/name", PlayerNamesEndpointTest::auth);
            Assertions.assertEquals(400, response.code());
            Assertions.assertTrue(response.body().string().contains("\"error\":\"INVALID_UUID\""));
        });
    }

    @Test
    void batchNamesKeepsOrderAndNullsForUnknownIds() {
        UUID unknown = UUID.randomUUID();
        JavalinTest.test(TestServers.standard().javalin(), (server, client) -> {
            Response response = client.post("/players/names",
                    "{\"ids\":[\"" + TestServers.BEDROCK + "\",\"" + unknown + "\",\"" + TestServers.NOTCH + "\"]}",
                    PlayerNamesEndpointTest::auth);
            Assertions.assertEquals(200, response.code());
            Assertions.assertEquals("{\"players\":["
                            + "{\"id\":\"" + TestServers.BEDROCK + "\",\"name\":\".Cool Guy 123\"},"
                            + "{\"id\":\"" + unknown + "\",\"name\":null},"
                            + "{\"id\":\"" + TestServers.NOTCH + "\",\"name\":\"Notch\"}]}",
                    response.body().string());
        });
    }

    @Test
    void batchUuidsResolvesBedrockNamesWithSpaces() {
        JavalinTest.test(TestServers.standard().javalin(), (server, client) -> {
            Response response = client.post("/players/uuids",
                    "{\"names\":[\".Cool Guy 123\",\"nobody\",\"Notch\"]}", PlayerNamesEndpointTest::auth);
            Assertions.assertEquals(200, response.code());
            Assertions.assertEquals("{\"players\":["
                            + "{\"id\":\"" + TestServers.BEDROCK + "\",\"name\":\".Cool Guy 123\"},"
                            + "{\"id\":null,\"name\":\"nobody\"},"
                            + "{\"id\":\"" + TestServers.NOTCH + "\",\"name\":\"Notch\"}]}",
                    response.body().string());
        });
    }

    @Test
    void batchWithAMalformedBodyIs400() {
        JavalinTest.test(TestServers.standard().javalin(), (server, client) -> {
            Response response = client.post("/players/names", "{\"ids\":[\"steve\"]}", PlayerNamesEndpointTest::auth);
            Assertions.assertEquals(400, response.code());
            Response notJson = client.post("/players/uuids", "nope", PlayerNamesEndpointTest::auth);
            Assertions.assertEquals(400, notJson.code());
        });
    }

    @Test
    void batchNamesWithANullIdElementIs400() {
        JavalinTest.test(TestServers.standard().javalin(), (server, client) -> {
            Response response = client.post("/players/names", "{\"ids\":[null]}", PlayerNamesEndpointTest::auth);
            Assertions.assertEquals(400, response.code());
        });
    }

    @Test
    void batchUuidsWithANullNameElementIs400() {
        JavalinTest.test(TestServers.standard().javalin(), (server, client) -> {
            Response response = client.post("/players/uuids", "{\"names\":[null]}", PlayerNamesEndpointTest::auth);
            Assertions.assertEquals(400, response.code());
        });
    }

    @Test
    void batchRequiresTheSecretToo() {
        JavalinTest.test(TestServers.standard().javalin(), (server, client) -> {
            Assertions.assertEquals(401, client.post("/players/names", "{\"ids\":[]}").code());
        });
    }

    private static String idsBody(int count) {
        List<String> ids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ids.add("\"" + new UUID(0L, i) + "\"");
        }
        return "{\"ids\":[" + String.join(",", ids) + "]}";
    }

    private static String namesBody(int count) {
        List<String> names = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            names.add("\"player" + i + "\"");
        }
        return "{\"names\":[" + String.join(",", names) + "]}";
    }

    @Test
    void aBatchOverTheCapIs400() {
        JavalinTest.test(TestServers.withNoPlayers().javalin(), (server, client) -> {
            Response ids = client.post("/players/names", idsBody(QueryServiceServer.MAX_BATCH + 1),
                    PlayerNamesEndpointTest::auth);
            Assertions.assertEquals(400, ids.code());
            Assertions.assertTrue(ids.body().string().contains("\"error\":\"BATCH_TOO_LARGE\""));

            Response names = client.post("/players/uuids", namesBody(QueryServiceServer.MAX_BATCH + 1),
                    PlayerNamesEndpointTest::auth);
            Assertions.assertEquals(400, names.code());
            Assertions.assertTrue(names.body().string().contains("\"error\":\"BATCH_TOO_LARGE\""));
        });
    }

    @Test
    void aBatchExactlyAtTheCapIsAccepted() {
        JavalinTest.test(TestServers.withNoPlayers().javalin(), (server, client) -> {
            Assertions.assertEquals(200, client.post("/players/names",
                    idsBody(QueryServiceServer.MAX_BATCH), PlayerNamesEndpointTest::auth).code());
            Assertions.assertEquals(200, client.post("/players/uuids",
                    namesBody(QueryServiceServer.MAX_BATCH), PlayerNamesEndpointTest::auth).code());
        });
    }

    @Test
    void aStalledNameResolverIs504NotAHang() {
        JavalinTest.test(TestServers.withStalledNames(Duration.ofMillis(200)).javalin(), (server, client) -> {
            long started = System.nanoTime();
            Response response = client.get("/players/" + TestServers.NOTCH + "/name",
                    PlayerNamesEndpointTest::auth);
            long elapsedMs = (System.nanoTime() - started) / 1_000_000;
            Assertions.assertEquals(504, response.code());
            Assertions.assertTrue(response.body().string().contains("\"error\":\"UPSTREAM_TIMEOUT\""));
            Assertions.assertTrue(elapsedMs < 5_000, "took " + elapsedMs + "ms");
        });
    }
}
