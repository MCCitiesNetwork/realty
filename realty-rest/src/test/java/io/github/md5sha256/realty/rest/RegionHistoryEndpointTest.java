package io.github.md5sha256.realty.rest;

import io.github.md5sha256.realty.database.entity.HistoryEntry;
import io.javalin.testtools.JavalinTest;
import okhttp3.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

class RegionHistoryEndpointTest {

    private static final UUID BUYER = UUID.fromString("11110000-0000-0000-0000-000000000001");
    private static final UUID AUTHORITY = UUID.fromString("22220000-0000-0000-0000-000000000002");
    private static final String URL = "/v1/region/history?world=world&region=downtown_plot_14";

    @Test
    void discriminatesAFreeholdEntryByKind() {
        HistoryEntry.Freehold entry = new HistoryEntry.Freehold(
                "BUY", LocalDateTime.of(2026, 8, 30, 14, 2, 11), BUYER, AUTHORITY, 21500.0);
        RealtyRestServer server = TestServers.withHistory(List.of(entry), 1, Map.of(BUYER, "Alice"));
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get(URL);
            Assertions.assertEquals(200, response.code());
            String body = response.body().string();
            Assertions.assertTrue(body.contains("\"kind\":\"freehold\""), body);
            Assertions.assertTrue(body.contains("\"eventType\":\"BUY\""), body);
            Assertions.assertTrue(body.contains("\"eventTime\":\"2026-08-30T14:02:11Z\""), body);
            Assertions.assertTrue(body.contains("\"price\":21500.0"), body);
            Assertions.assertTrue(body.contains("\"name\":\"Alice\""), body);
        });
    }

    @Test
    void discriminatesALeaseholdEntryByKind() {
        HistoryEntry.Leasehold entry = new HistoryEntry.Leasehold(
                "RENT", LocalDateTime.of(2026, 8, 12, 9, 40), BUYER, AUTHORITY,
                800.0, 604800L, 3);
        RealtyRestServer server = TestServers.withHistory(List.of(entry), 1, Map.of());
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            String body = client.get(URL).body().string();
            Assertions.assertTrue(body.contains("\"kind\":\"leasehold\""), body);
            Assertions.assertTrue(body.contains("\"durationSeconds\":604800"), body);
            Assertions.assertTrue(body.contains("\"extensionsRemaining\":3"), body);
        });
    }

    @Test
    void discriminatesAnAgentEntryByKind() {
        HistoryEntry.Agent entry = new HistoryEntry.Agent(
                "AGENT_ADD", LocalDateTime.of(2026, 8, 1, 18, 0), BUYER, AUTHORITY);
        RealtyRestServer server = TestServers.withHistory(List.of(entry), 1, Map.of());
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            String body = client.get(URL).body().string();
            Assertions.assertTrue(body.contains("\"kind\":\"agent\""), body);
            Assertions.assertTrue(body.contains("\"agent\""), body);
            Assertions.assertTrue(body.contains("\"actor\""), body);
        });
    }

    @Test
    void carriesThePagingEnvelope() {
        RealtyRestServer server = TestServers.withHistory(List.of(), 25, Map.of());
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            String body = client.get(URL + "&pageSize=10").body().string();
            Assertions.assertTrue(body.contains("\"totalCount\":25"), body);
            Assertions.assertTrue(body.contains("\"totalPages\":3"), body);
        });
    }

    @Test
    void rejectsAnUnknownEventType() {
        RealtyRestServer server = TestServers.withHistory(List.of(), 0, Map.of());
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get(URL + "&type=NOT_A_TYPE");
            Assertions.assertEquals(400, response.code());
            Assertions.assertTrue(response.body().string().contains("INVALID_EVENT_TYPE"));
        });
    }

    @Test
    void acceptsAKnownEventTypeAndPassesItToTheBackend() {
        TestServers.HistoryStub stub = new TestServers.HistoryStub(List.of(), 0);
        RealtyRestServer server = TestServers.withHistory(stub, Map.of());
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Assertions.assertEquals(200, client.get(URL + "&type=BUY").code());
            Assertions.assertEquals("BUY", stub.eventType);
        });
    }

    @Test
    void rejectsASinceThatIsNotAnIsoInstant() {
        RealtyRestServer server = TestServers.withHistory(List.of(), 0, Map.of());
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get(URL + "&since=last-tuesday");
            Assertions.assertEquals(400, response.code());
            Assertions.assertTrue(response.body().string().contains("INVALID_SINCE"));
        });
    }

    @Test
    void passesAnIsoSinceToTheBackendAsUtc() {
        TestServers.HistoryStub stub = new TestServers.HistoryStub(List.of(), 0);
        RealtyRestServer server = TestServers.withHistory(stub, Map.of());
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Assertions.assertEquals(200, client.get(URL + "&since=2026-08-01T00:00:00Z").code());
            Assertions.assertEquals(LocalDateTime.of(2026, 8, 1, 0, 0), stub.since);
        });
    }

    @Test
    void rejectsAMalformedPlayerFilter() {
        RealtyRestServer server = TestServers.withHistory(List.of(), 0, Map.of());
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get(URL + "&player=zzzzzzzz-0000-0000-0000-000000000099");
            Assertions.assertEquals(400, response.code());
            Assertions.assertTrue(response.body().string().contains("MALFORMED_UUID"));
        });
    }

    @Test
    void returnsAnEmptyPageForARegionWithNoHistory() {
        RealtyRestServer server = TestServers.withHistory(List.of(), 0, Map.of());
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get(URL);
            Assertions.assertEquals(200, response.code(),
                    "a region nobody has traded is a valid answer, not a missing resource");
            Assertions.assertTrue(response.body().string().contains("\"entries\":[]"));
        });
    }
}
