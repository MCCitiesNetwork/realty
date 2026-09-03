package io.github.md5sha256.realty.rest;

import io.github.md5sha256.realty.database.entity.ActivityRow;
import io.javalin.testtools.JavalinTest;
import okhttp3.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

class ActivityEndpointTest {

    private static final UUID WORLD_ID = UUID.fromString("8f4d0000-0000-0000-0000-000000000001");
    private static final UUID ALICE = UUID.fromString("11110000-0000-0000-0000-000000000001");
    private static final UUID BOB = UUID.fromString("22220000-0000-0000-0000-000000000002");

    private static ActivityRow freehold(String region, String eventType) {
        return new ActivityRow("freehold", region, WORLD_ID, eventType,
                LocalDateTime.of(2026, 8, 30, 14, 2, 11), ALICE, BOB, 21500.0, null, null);
    }

    @Test
    void reportsEachEventWithItsRegionAndWorld() {
        RealtyRestServer server = TestServers.withActivity(
                List.of(freehold("plot_a", "BUY")), 1, Map.of(ALICE, "Alice"));
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/activity");
            Assertions.assertEquals(200, response.code());
            String body = response.body().string();
            Assertions.assertTrue(body.contains("\"kind\":\"freehold\""), body);
            Assertions.assertTrue(body.contains("\"worldGuardRegionId\":\"plot_a\""),
                    "a server-wide feed must name the region each event happened to: " + body);
            Assertions.assertTrue(body.contains("\"name\":\"world\""), body);
            Assertions.assertTrue(body.contains("\"eventTime\":\"2026-08-30T14:02:11Z\""), body);
            Assertions.assertTrue(body.contains("\"price\":21500.0"), body);
            Assertions.assertTrue(body.contains("\"Alice\""), body);
        });
    }

    @Test
    void carriesTheLeaseholdAndAgentShapesToo() {
        ActivityRow lease = new ActivityRow("leasehold", "plot_b", WORLD_ID, "RENT",
                LocalDateTime.of(2026, 8, 12, 9, 40), ALICE, BOB, 800.0, 604800L, 3);
        ActivityRow agent = new ActivityRow("agent", "plot_c", WORLD_ID, "AGENT_ADD",
                LocalDateTime.of(2026, 8, 1, 18, 0), ALICE, BOB, null, null, null);
        RealtyRestServer server = TestServers.withActivity(List.of(lease, agent), 2, Map.of());
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            String body = client.get("/v1/activity").body().string();
            Assertions.assertTrue(body.contains("\"kind\":\"leasehold\""), body);
            Assertions.assertTrue(body.contains("\"durationSeconds\":604800"), body);
            Assertions.assertTrue(body.contains("\"kind\":\"agent\""), body);
            Assertions.assertTrue(body.contains("\"agent\""), body);
        });
    }

    @Test
    void defaultsToTheTradeEventsRatherThanEveryAuditEntry() {
        TestServers.ActivityStub stub = new TestServers.ActivityStub(List.of(), 0);
        RealtyRestServer server = TestServers.withActivity(stub, Map.of());
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Assertions.assertEquals(200, client.get("/v1/activity").code());
            Assertions.assertEquals(List.of("BUY", "AUCTION_BUY", "OFFER_BUY", "RENT"),
                    stub.eventTypes,
                    "the default feed is a sales-and-lettings ticker, not an audit trail");
        });
    }

    @Test
    void narrowsToTheRequestedEventTypes() {
        TestServers.ActivityStub stub = new TestServers.ActivityStub(List.of(), 0);
        RealtyRestServer server = TestServers.withActivity(stub, Map.of());
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Assertions.assertEquals(200, client.get("/v1/activity?type=BUY&type=TERMINATE").code());
            Assertions.assertEquals(List.of("BUY", "TERMINATE"), stub.eventTypes);
        });
    }

    @Test
    void rejectsAnUnknownEventType() {
        RealtyRestServer server = TestServers.withActivity(List.of(), 0, Map.of());
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/activity?type=NOT_A_TYPE");
            Assertions.assertEquals(400, response.code());
            Assertions.assertTrue(response.body().string().contains("INVALID_EVENT_TYPE"));
        });
    }

    @Test
    void narrowsToOneWorldAndRejectsAnUnknownOne() {
        TestServers.ActivityStub stub = new TestServers.ActivityStub(List.of(), 0);
        RealtyRestServer server = TestServers.withActivity(stub, Map.of());
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Assertions.assertEquals(200, client.get("/v1/activity?world=world").code());
            Assertions.assertEquals(WORLD_ID, stub.worldId);
            Assertions.assertEquals(404, client.get("/v1/activity?world=atlantis").code());
        });
    }

    @Test
    void passesAnIsoSinceToTheQuery() {
        TestServers.ActivityStub stub = new TestServers.ActivityStub(List.of(), 0);
        RealtyRestServer server = TestServers.withActivity(stub, Map.of());
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Assertions.assertEquals(200, client.get("/v1/activity?since=2026-08-01T00:00:00Z").code());
            Assertions.assertEquals(LocalDateTime.of(2026, 8, 1, 0, 0), stub.since);
        });
    }

    @Test
    void carriesThePagingEnvelope() {
        RealtyRestServer server = TestServers.withActivity(List.of(), 25, Map.of());
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            String body = client.get("/v1/activity?pageSize=10").body().string();
            Assertions.assertTrue(body.contains("\"totalCount\":25"), body);
            Assertions.assertTrue(body.contains("\"totalPages\":3"), body);
        });
    }

    @Test
    void returnsAnEmptyFeedOnAQuietServer() {
        RealtyRestServer server = TestServers.withActivity(List.of(), 0, Map.of());
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/activity");
            Assertions.assertEquals(200, response.code());
            Assertions.assertTrue(response.body().string().contains("\"events\":[]"));
        });
    }
}
