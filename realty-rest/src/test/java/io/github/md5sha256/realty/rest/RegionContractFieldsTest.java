package io.github.md5sha256.realty.rest;

import io.github.md5sha256.realty.api.RealtyBackend;
import io.github.md5sha256.realty.api.RegionState;
import io.github.md5sha256.realty.database.entity.FreeholdContractAuctionEntity;
import io.github.md5sha256.realty.database.entity.FreeholdContractBid;
import io.github.md5sha256.realty.database.entity.FreeholdContractEntity;
import io.github.md5sha256.realty.database.entity.LeaseholdContractEntity;
import io.javalin.testtools.JavalinTest;
import okhttp3.Response;
import org.junit.jupiter.api.Assertions;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The contract fields the entities already carry and {@code /v1/region} now serialises.
 */
class RegionContractFieldsTest {

    private static final UUID LANDLORD = UUID.fromString("11110000-0000-0000-0000-000000000001");
    private static final UUID AUCTIONEER = UUID.fromString("22220000-0000-0000-0000-000000000002");
    private static final String URL = "/v1/region?world=world&region=downtown_plot_14";

    private static void get(@NotNull RealtyRestServer server, @NotNull Consumer<String> assertions) {
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get(URL);
            Assertions.assertEquals(200, response.code());
            assertions.accept(response.body().string());
        });
    }

    @Test
    void reportsWhetherAFreeholdIsAcceptingOffers() {
        FreeholdContractEntity freehold = new FreeholdContractEntity(1, LANDLORD, null, 25000.0, false);
        RealtyRestServer server = TestServers.withRegionInfo(
                new RealtyBackend.RegionInfo(freehold, null, null, null, null),
                RegionState.FOR_SALE,
                TestServers.stubModule(Map.of(), Map.of(), Map.of()));
        get(server, body -> Assertions.assertTrue(body.contains("\"acceptingOffers\":false"),
                "expected acceptingOffers in: " + body));
    }

    @Test
    void reportsWhetherALeaseholdIsAcceptingTenants() {
        RealtyRestServer server = TestServers.withRegionInfo(
                new RealtyBackend.RegionInfo(null, leasehold(null, null), null, null, null),
                RegionState.FOR_LEASE,
                TestServers.stubModule(Map.of(), Map.of(), Map.of()));
        get(server, body -> Assertions.assertTrue(body.contains("\"acceptingTenants\":true"),
                "expected acceptingTenants in: " + body));
    }

    @Test
    void reportsAPendingLeaseholdTermination() {
        LeaseholdContractEntity leasehold =
                leasehold(LocalDateTime.of(2026, 10, 1, 12, 0), "LANDLORD");
        RealtyRestServer server = TestServers.withRegionInfo(
                new RealtyBackend.RegionInfo(null, leasehold, null, null, null),
                RegionState.LEASED,
                TestServers.stubModule(Map.of(), Map.of(), Map.of()));
        get(server, body -> {
            Assertions.assertTrue(body.contains("\"terminationEffectiveDate\":\"2026-10-01T12:00:00Z\""),
                    "expected the termination date in: " + body);
            Assertions.assertTrue(body.contains("\"terminatedByRole\":\"LANDLORD\""),
                    "expected the terminating role in: " + body);
        });
    }

    @Test
    void reportsNullTerminationFieldsForALeaseholdNobodyHasTerminated() {
        RealtyRestServer server = TestServers.withRegionInfo(
                new RealtyBackend.RegionInfo(null, leasehold(null, null), null, null, null),
                RegionState.LEASED,
                TestServers.stubModule(Map.of(), Map.of(), Map.of()));
        get(server, body -> {
            Assertions.assertTrue(body.contains("\"terminationEffectiveDate\":null"),
                    "expected a null termination date in: " + body);
            Assertions.assertTrue(body.contains("\"terminatedByRole\":null"),
                    "expected a null terminating role in: " + body);
        });
    }

    @Test
    void reportsTheAuctionsTermsAlongsideItsDeadline() {
        FreeholdContractAuctionEntity auction = new FreeholdContractAuctionEntity(
                1, 1, AUCTIONEER,
                LocalDateTime.of(2026, 9, 1, 9, 0),
                86400L, 3600L,
                LocalDateTime.of(2026, 9, 3, 9, 0),
                5000.0, 250.0, false);
        RealtyRestServer server = TestServers.withRegionInfo(
                new RealtyBackend.RegionInfo(null, null, auction, null, null),
                RegionState.FOR_SALE,
                TestServers.stubModule(Map.of(AUCTIONEER, "Auctioneer"), Map.of(), Map.of()));
        get(server, body -> {
            Assertions.assertTrue(body.contains("\"startDate\":\"2026-09-01T09:00:00Z\""),
                    "expected the auction start date in: " + body);
            Assertions.assertTrue(body.contains("\"minBid\":5000.0"), "expected minBid in: " + body);
            Assertions.assertTrue(body.contains("\"minStep\":250.0"), "expected minStep in: " + body);
            Assertions.assertTrue(body.contains("\"biddingDurationSeconds\":86400"),
                    "expected biddingDurationSeconds in: " + body);
            Assertions.assertTrue(body.contains("\"paymentDurationSeconds\":3600"),
                    "expected paymentDurationSeconds in: " + body);
            Assertions.assertTrue(body.contains("\"name\":\"Auctioneer\""),
                    "expected the auctioneer resolved to a name in: " + body);
        });
    }

    @Test
    void resolvesTheAuctioneerAlongsideTheHighestBidder() {
        UUID bidder = UUID.fromString("33330000-0000-0000-0000-000000000003");
        FreeholdContractAuctionEntity auction = new FreeholdContractAuctionEntity(
                1, 1, AUCTIONEER,
                LocalDateTime.of(2026, 9, 1, 9, 0),
                86400L, 3600L,
                LocalDateTime.of(2026, 9, 3, 9, 0),
                5000.0, 250.0, false);
        FreeholdContractBid bid = new FreeholdContractBid(
                1, bidder, 6000.0, LocalDateTime.of(2026, 9, 2, 9, 0));
        RealtyRestServer server = TestServers.withRegionInfo(
                new RealtyBackend.RegionInfo(null, null, auction, null, bid),
                RegionState.FOR_SALE,
                TestServers.stubModule(
                        Map.of(AUCTIONEER, "Auctioneer", bidder, "Bidder"), Map.of(), Map.of()));
        get(server, body -> {
            Assertions.assertTrue(body.contains("\"Auctioneer\""), "expected the auctioneer in: " + body);
            Assertions.assertTrue(body.contains("\"Bidder\""), "expected the bidder in: " + body);
        });
    }

    private static LeaseholdContractEntity leasehold(LocalDateTime terminationEffectiveDate,
                                                     String terminatedByRole) {
        return new LeaseholdContractEntity(
                1, LANDLORD, null, 800.0, 604800L,
                LocalDateTime.of(2026, 8, 1, 0, 0),
                LocalDateTime.of(2026, 8, 8, 0, 0),
                0, 3,
                terminationEffectiveDate, terminatedByRole, true);
    }
}
