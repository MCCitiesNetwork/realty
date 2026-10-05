package io.github.md5sha256.realty.adapter.query;

import io.javalin.testtools.JavalinTest;
import io.javalin.testtools.Request;
import io.javalin.testtools.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

class AccountNamesEndpointTest {

    private static Request.Builder auth(Request.Builder req) {
        return req.header(QueryServiceServer.SECRET_HEADER, TestServers.SECRET);
    }

    @Test
    void names_comeBackInTheOrderAsked() {
        JavalinTest.test(TestServers.standard().javalin(), (server, client) -> {
            Response response = client.post("/accounts/names", "{\"ids\":[42,77,5]}",
                    AccountNamesEndpointTest::auth);
            Assertions.assertEquals(200, response.code());
            Assertions.assertEquals("{\"accounts\":["
                            + "{\"id\":42,\"name\":\"GovSecurity\"},"
                            + "{\"id\":77,\"name\":null},"
                            + "{\"id\":5,\"name\":\"Bob \\\"the\\\" <b>Builder</b>\"}]}",
                    response.body().string());
        });
    }

    @Test
    void unknownAccount_hasANullName() {
        JavalinTest.test(TestServers.standard().javalin(), (server, client) -> {
            Response response = client.post("/accounts/names", "{\"ids\":[77]}", AccountNamesEndpointTest::auth);
            Assertions.assertEquals(200, response.code());
            Assertions.assertEquals("{\"accounts\":[{\"id\":77,\"name\":null}]}", response.body().string());
        });
    }

    @Test
    void emptyList_isAnEmptyList() {
        JavalinTest.test(TestServers.standard().javalin(), (server, client) -> {
            Response response = client.post("/accounts/names", "{\"ids\":[]}", AccountNamesEndpointTest::auth);
            Assertions.assertEquals(200, response.code());
            Assertions.assertEquals("{\"accounts\":[]}", response.body().string());
        });
    }

    @Test
    void idThatIsNotANumber_is400() {
        JavalinTest.test(TestServers.standard().javalin(), (server, client) -> {
            for (String body : List.of("{\"ids\":[\"x\"]}", "{\"ids\":[null]}", "{\"ids\":[1.5]}",
                    "{\"ids\":[99999999999]}")) {
                Response response = client.post("/accounts/names", body, AccountNamesEndpointTest::auth);
                Assertions.assertEquals(400, response.code(), body);
                Assertions.assertTrue(response.body().string().contains("\"error\":\"INVALID_ACCOUNT_ID\""), body);
            }
        });
    }

    @Test
    void bodyWithoutIds_is400() {
        JavalinTest.test(TestServers.standard().javalin(), (server, client) -> {
            Response response = client.post("/accounts/names", "{}", AccountNamesEndpointTest::auth);
            Assertions.assertEquals(400, response.code());
            Assertions.assertTrue(response.body().string().contains("\"error\":\"INVALID_BODY\""));
        });
    }

    private static String idsBody(int count) {
        List<String> ids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ids.add(Integer.toString(i));
        }
        return "{\"ids\":[" + String.join(",", ids) + "]}";
    }

    @Test
    void tooManyIds_isRefused() {
        JavalinTest.test(TestServers.standard().javalin(), (server, client) -> {
            Response over = client.post("/accounts/names", idsBody(QueryServiceServer.MAX_BATCH + 1),
                    AccountNamesEndpointTest::auth);
            Assertions.assertEquals(400, over.code());
            Assertions.assertTrue(over.body().string().contains("\"error\":\"BATCH_TOO_LARGE\""));
            Assertions.assertEquals(200, client.post("/accounts/names", idsBody(QueryServiceServer.MAX_BATCH),
                    AccountNamesEndpointTest::auth).code());
        });
    }

    @Test
    void withoutTheSecret_is401() {
        JavalinTest.test(TestServers.standard().javalin(), (server, client) -> {
            Assertions.assertEquals(401, client.post("/accounts/names", "{\"ids\":[]}").code());
        });
    }
}
