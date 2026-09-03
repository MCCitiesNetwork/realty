package io.github.md5sha256.realty.adapter.query;

import io.javalin.testtools.JavalinTest;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;

/** The three routes added for the v1.x REST endpoints. */
class RegionQueryEndpointsTest {

    private static Request.Builder auth(Request.Builder req) {
        return req.header(QueryServiceServer.SECRET_HEADER, TestServers.SECRET);
    }

    private static String at(String query) {
        return "/regions/" + TestServers.WORLD + "/at?" + query;
    }

    @Test
    void omittingYRunsAColumnTestAndSaysSo() {
        JavalinTest.test(TestServers.standard().javalin(), (server, client) -> {
            Response response = client.get(at("x=110&z=-70"), RegionQueryEndpointsTest::auth);
            Assertions.assertEquals(200, response.code());
            Assertions.assertEquals(
                    "{\"test\":\"column\",\"regions\":[\"downtown_plot_14\",\"annex\"]}",
                    response.body().string());
        });
    }

    @Test
    void givingYRunsAPointTestAndSaysSo() {
        JavalinTest.test(TestServers.standard().javalin(), (server, client) -> {
            Response response = client.get(at("x=110&y=300&z=-70"), RegionQueryEndpointsTest::auth);
            Assertions.assertEquals(200, response.code());
            Assertions.assertEquals("{\"test\":\"point\",\"regions\":[\"annex\"]}",
                    response.body().string());
        });
    }

    @Test
    void aBlockInNoRegionIsAnEmptyListNotA404() {
        JavalinTest.test(TestServers.standard().javalin(), (server, client) -> {
            Response response = client.get(at("x=9000&z=9000"), RegionQueryEndpointsTest::auth);
            Assertions.assertEquals(200, response.code());
            Assertions.assertEquals("{\"test\":\"column\",\"regions\":[]}", response.body().string());
        });
    }

    @Test
    void anUnknownWorldIs404() {
        JavalinTest.test(TestServers.standard().javalin(), (server, client) -> {
            Response response = client.get(
                    "/regions/00000000-0000-0000-0000-0000000000ff/at?x=1&z=1",
                    RegionQueryEndpointsTest::auth);
            Assertions.assertEquals(404, response.code());
            Assertions.assertTrue(response.body().string().contains("\"error\":\"WORLD_NOT_FOUND\""));
        });
    }

    @Test
    void aMissingCoordinateIs400() {
        JavalinTest.test(TestServers.standard().javalin(), (server, client) -> {
            Response response = client.get(at("x=110"), RegionQueryEndpointsTest::auth);
            Assertions.assertEquals(400, response.code());
            Assertions.assertTrue(response.body().string().contains("\"error\":\"INVALID_COORDINATE\""));
        });
    }

    @Test
    void aNonIntegerCoordinateIs400() {
        JavalinTest.test(TestServers.standard().javalin(), (server, client) -> {
            for (String query : new String[]{"x=1.5&z=1", "x=1&z=up", "x=1&z=1&y=sixty"}) {
                Response response = client.get(at(query), RegionQueryEndpointsTest::auth);
                Assertions.assertEquals(400, response.code(), query);
                Assertions.assertTrue(response.body().string().contains("\"error\":\"INVALID_COORDINATE\""),
                        query);
            }
        });
    }

    @Test
    void anEmptyYIsTreatedAsOmittedRatherThanRejected() {
        // A client templating "&y=" with nothing to substitute asks the column question.
        JavalinTest.test(TestServers.standard().javalin(), (server, client) -> {
            Response response = client.get(at("x=110&z=-70&y="), RegionQueryEndpointsTest::auth);
            Assertions.assertEquals(200, response.code());
            Assertions.assertTrue(response.body().string().contains("\"test\":\"column\""));
        });
    }

    @Test
    void membersReportsBothDomains() {
        JavalinTest.test(TestServers.standard().javalin(), (server, client) -> {
            Response response = client.get(
                    "/regions/" + TestServers.WORLD + "/downtown_plot_14/members",
                    RegionQueryEndpointsTest::auth);
            Assertions.assertEquals(200, response.code());
            Assertions.assertEquals(
                    "{\"owners\":{\"playerIds\":[\"" + TestServers.NOTCH + "\"],\"playerNames\":[],"
                            + "\"groups\":[\"staff\"]},"
                            + "\"members\":{\"playerIds\":[\"" + TestServers.BEDROCK + "\"],"
                            + "\"playerNames\":[\"legacyname\"],\"groups\":[]}}",
                    response.body().string());
        });
    }

    @Test
    void membersOfAnUnknownRegionIs404() {
        JavalinTest.test(TestServers.standard().javalin(), (server, client) -> {
            Response response = client.get(
                    "/regions/" + TestServers.WORLD + "/plot_9/members", RegionQueryEndpointsTest::auth);
            Assertions.assertEquals(404, response.code());
            Assertions.assertTrue(response.body().string().contains("\"error\":\"REGION_NOT_FOUND\""));
        });
    }

    @Test
    void batchDimensionsOmitsUnknownIds() {
        JavalinTest.test(TestServers.standard().javalin(), (server, client) -> {
            Response response = client.post("/regions/" + TestServers.WORLD + "/dimensions",
                    "{\"ids\":[\"downtown_plot_14\",\"nowhere\"]}", RegionQueryEndpointsTest::auth);
            Assertions.assertEquals(200, response.code());
            String body = response.body().string();
            Assertions.assertTrue(body.contains("\"downtown_plot_14\""), body);
            Assertions.assertFalse(body.contains("nowhere"), body);
        });
    }

    @Test
    void batchDimensionsRejectsAnOversizedBatch() {
        JavalinTest.test(TestServers.standard().javalin(), (server, client) -> {
            StringBuilder ids = new StringBuilder();
            for (int i = 0; i <= QueryServiceServer.MAX_BATCH; i++) {
                ids.append(i == 0 ? "" : ",").append("\"r").append(i).append("\"");
            }
            Response response = client.post("/regions/" + TestServers.WORLD + "/dimensions",
                    "{\"ids\":[" + ids + "]}", RegionQueryEndpointsTest::auth);
            Assertions.assertEquals(400, response.code());
            Assertions.assertTrue(response.body().string().contains("\"error\":\"BATCH_TOO_LARGE\""));
        });
    }

    @Test
    void batchDimensionsRejectsABodyThatIsNotAnIdList() {
        JavalinTest.test(TestServers.standard().javalin(), (server, client) -> {
            Response response = client.post("/regions/" + TestServers.WORLD + "/dimensions",
                    "{\"regions\":[]}", RegionQueryEndpointsTest::auth);
            Assertions.assertEquals(400, response.code());
            Assertions.assertTrue(response.body().string().contains("\"error\":\"INVALID_BODY\""));
        });
    }

    @Test
    void aStalledMainThreadIs504OnEveryNewRoute() {
        QueryServiceServer server = TestServers.withStalledMainThread(Duration.ofMillis(200));
        JavalinTest.test(server.javalin(), (s, client) -> {
            Response point = client.get(at("x=110&z=-70"), RegionQueryEndpointsTest::auth);
            Assertions.assertEquals(504, point.code());
            Response members = client.get(
                    "/regions/" + TestServers.WORLD + "/downtown_plot_14/members",
                    RegionQueryEndpointsTest::auth);
            Assertions.assertEquals(504, members.code());
            Response batch = client.post("/regions/" + TestServers.WORLD + "/dimensions",
                    "{\"ids\":[\"downtown_plot_14\"]}", RegionQueryEndpointsTest::auth);
            Assertions.assertEquals(504, batch.code());
        });
    }
}
