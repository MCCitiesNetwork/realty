package io.github.md5sha256.realty.rest;

import io.github.md5sha256.realty.database.entity.ActiveAuctionRow;
import io.github.md5sha256.realty.database.entity.AuctionSort;
import io.javalin.testtools.JavalinTest;
import io.javalin.testtools.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

class AuctionsEndpointTest {

    private static final UUID WORLD_ID = UUID.fromString("8f4d0000-0000-0000-0000-000000000001");
    private static final UUID AUCTIONEER = UUID.fromString("22220000-0000-0000-0000-000000000002");
    private static final UUID BIDDER = UUID.fromString("33330000-0000-0000-0000-000000000003");

    private static ActiveAuctionRow row(String regionId, Double highestBid) {
        return new ActiveAuctionRow(
                regionId, WORLD_ID, AUCTIONEER,
                LocalDateTime.of(2026, 9, 1, 9, 0),
                86400L, 3600L, 5000.0, 250.0,
                highestBid == null ? null : BIDDER,
                highestBid,
                highestBid == null ? null : LocalDateTime.of(2026, 9, 2, 9, 0),
                highestBid == null ? 0 : 1,
                LocalDateTime.of(2026, 9, 3, 9, 0));
    }

    @Test
    void listsEachLiveAuctionWithItsTermsAndDeadline() {
        RealtyRestServer server = TestServers.withAuctions(
                List.of(row("plot_a", 6000.0)), 1, Map.of(AUCTIONEER, "Auctioneer", BIDDER, "Bidder"));
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/auctions");
            Assertions.assertEquals(200, response.code());
            String body = response.body().string();
            Assertions.assertTrue(body.contains("\"worldGuardRegionId\":\"plot_a\""), body);
            Assertions.assertTrue(body.contains("\"minBid\":5000.0"), body);
            Assertions.assertTrue(body.contains("\"minStep\":250.0"), body);
            Assertions.assertTrue(body.contains("\"endDate\":\"2026-09-03T09:00:00Z\""), body);
            Assertions.assertTrue(body.contains("\"amount\":6000.0"), body);
            Assertions.assertTrue(body.contains("\"bidderCount\":1"), body);
            Assertions.assertTrue(body.contains("\"Auctioneer\""), body);
        });
    }

    @Test
    void reportsANullHighestBidForAnAuctionNobodyHasBidOn() {
        RealtyRestServer server = TestServers.withAuctions(List.of(row("plot_b", null)), 1, Map.of());
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            String body = client.get("/v1/auctions").body().string();
            Assertions.assertTrue(body.contains("\"highestBid\":null"), body);
            Assertions.assertTrue(body.contains("\"bidderCount\":0"), body);
        });
    }

    @Test
    void defaultsToEndingSoonest() {
        TestServers.AuctionStub stub = new TestServers.AuctionStub(List.of(), 0);
        RealtyRestServer server = TestServers.withAuctions(stub, Map.of());
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Assertions.assertEquals(200, client.get("/v1/auctions").code());
            Assertions.assertEquals(AuctionSort.ENDING_SOON, stub.sort);
        });
    }

    @Test
    void acceptsTheHighestBidSort() {
        TestServers.AuctionStub stub = new TestServers.AuctionStub(List.of(), 0);
        RealtyRestServer server = TestServers.withAuctions(stub, Map.of());
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Assertions.assertEquals(200, client.get("/v1/auctions?sort=highest_bid").code());
            Assertions.assertEquals(AuctionSort.HIGHEST_BID, stub.sort);
        });
    }

    @Test
    void rejectsAnUnknownSort() {
        RealtyRestServer server = TestServers.withAuctions(List.of(), 0, Map.of());
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/auctions?sort=cheapest");
            Assertions.assertEquals(400, response.code());
            Assertions.assertTrue(response.body().string().contains("INVALID_SORT"));
        });
    }

    @Test
    void narrowsToOneWorldAndRejectsAnUnknownOne() {
        TestServers.AuctionStub stub = new TestServers.AuctionStub(List.of(), 0);
        RealtyRestServer server = TestServers.withAuctions(stub, Map.of());
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Assertions.assertEquals(200, client.get("/v1/auctions?world=world").code());
            Assertions.assertEquals(WORLD_ID, stub.worldId);
            Response unknown = client.get("/v1/auctions?world=atlantis");
            Assertions.assertEquals(404, unknown.code());
            Assertions.assertTrue(unknown.body().string().contains("WORLD_NOT_FOUND"));
        });
    }

    @Test
    void carriesThePagingEnvelope() {
        RealtyRestServer server = TestServers.withAuctions(List.of(), 25, Map.of());
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            String body = client.get("/v1/auctions?pageSize=10").body().string();
            Assertions.assertTrue(body.contains("\"totalCount\":25"), body);
            Assertions.assertTrue(body.contains("\"totalPages\":3"), body);
        });
    }

    @Test
    void returnsAnEmptyPageWhenNothingIsUnderTheHammer() {
        RealtyRestServer server = TestServers.withAuctions(List.of(), 0, Map.of());
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/auctions");
            Assertions.assertEquals(200, response.code());
            Assertions.assertTrue(response.body().string().contains("\"auctions\":[]"));
        });
    }
}
