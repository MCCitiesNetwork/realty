package io.github.md5sha256.realty.rest;

import io.github.md5sha256.realty.database.entity.PlotOwnerCount;
import io.javalin.testtools.JavalinTest;
import okhttp3.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

class OwnersLeaderboardEndpointTest {

    private static final UUID TOP = UUID.fromString("11110000-0000-0000-0000-000000000001");
    private static final UUID SECOND = UUID.fromString("22220000-0000-0000-0000-000000000002");

    @Test
    void ranksOwnersInTheOrderTheQueryReturns() {
        RealtyRestServer server = TestServers.withOwnerCounts(
                List.of(new PlotOwnerCount(TOP, 12), new PlotOwnerCount(SECOND, 5)), 2,
                Map.of(TOP, "Alice", SECOND, "Bob"));
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/leaderboard/owners");
            Assertions.assertEquals(200, response.code());
            String body = response.body().string();
            Assertions.assertTrue(body.indexOf("Alice") < body.indexOf("Bob"),
                    "expected the query's ranking to survive: " + body);
            Assertions.assertTrue(body.contains("\"plotCount\":12"), body);
            Assertions.assertTrue(body.contains("\"plotCount\":5"), body);
        });
    }

    @Test
    void numbersEachRowByItsPositionAcrossTheWholeLeaderboard() {
        RealtyRestServer server = TestServers.withOwnerCounts(
                List.of(new PlotOwnerCount(SECOND, 5)), 11, Map.of());
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/leaderboard/owners?page=2&pageSize=10");
            Assertions.assertEquals(200, response.code());
            String body = response.body().string();
            Assertions.assertTrue(body.contains("\"rank\":11"),
                    "the first row of page 2 at pageSize 10 ranks 11th: " + body);
        });
    }

    @Test
    void carriesThePagingEnvelopeTheOtherListingsUse() {
        RealtyRestServer server = TestServers.withOwnerCounts(
                List.of(new PlotOwnerCount(TOP, 12)), 3, Map.of());
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/leaderboard/owners?pageSize=1");
            String body = response.body().string();
            Assertions.assertTrue(body.contains("\"page\":1"), body);
            Assertions.assertTrue(body.contains("\"pageSize\":1"), body);
            Assertions.assertTrue(body.contains("\"totalCount\":3"), body);
            Assertions.assertTrue(body.contains("\"totalPages\":3"), body);
        });
    }

    @Test
    void returnsAnEmptyPageWhenNobodyHoldsAnything() {
        RealtyRestServer server = TestServers.withOwnerCounts(List.of(), 0, Map.of());
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/leaderboard/owners");
            Assertions.assertEquals(200, response.code());
            Assertions.assertTrue(response.body().string().contains("\"owners\":[]"));
        });
    }

    @Test
    void reportsANullNameWhenTheModuleCannotResolveTheOwner() {
        RealtyRestServer server = TestServers.withOwnerCounts(
                List.of(new PlotOwnerCount(TOP, 12)), 1, Map.of());
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/leaderboard/owners");
            Assertions.assertEquals(200, response.code(),
                    "an unresolvable name degrades to null, it does not fail the request");
            String body = response.body().string();
            Assertions.assertTrue(body.contains("\"name\":null"), body);
            Assertions.assertTrue(body.contains(TOP.toString()), body);
        });
    }
}
