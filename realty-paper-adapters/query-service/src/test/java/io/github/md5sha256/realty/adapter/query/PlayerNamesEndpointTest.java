package io.github.md5sha256.realty.adapter.query;

import io.javalin.testtools.JavalinTest;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

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
    void batchRequiresTheSecretToo() {
        JavalinTest.test(TestServers.standard().javalin(), (server, client) -> {
            Assertions.assertEquals(401, client.post("/players/names", "{\"ids\":[]}").code());
        });
    }
}
