package io.github.md5sha256.realty.database;

import io.github.md5sha256.realty.database.entity.ActiveAuctionRow;
import io.github.md5sha256.realty.database.entity.AuctionSort;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The live-auction listing behind {@code /v1/auctions}.
 *
 * <p>The two things only a real database can settle are exercised here: that the
 * correlated subqueries still yield a row for an auction nobody has bid on, and that
 * {@code endDate} is genuinely computed by {@code DATE_ADD} over the bidding duration
 * rather than assumed in Java.</p>
 */
class ActiveAuctionQueryTest extends AbstractDatabaseTest {

    private static final UUID WORLD_ID = UUID.fromString("8f4d1c2e-0000-0000-0000-000000000051");
    private static final UUID OTHER_WORLD = UUID.fromString("8f4d1c2e-0000-0000-0000-000000000052");
    private static final UUID AUTHORITY = UUID.fromString("3a1c88f0-0000-0000-0000-000000000050");
    private static final UUID BIDDER = UUID.fromString("3a1c88f0-0000-0000-0000-000000000051");
    private static final UUID OTHER_BIDDER = UUID.fromString("3a1c88f0-0000-0000-0000-000000000052");

    @BeforeEach
    void seed() {
        Assertions.assertTrue(logic.createFreehold("plot_quiet", WORLD_ID, 1000.0, AUTHORITY, null));
        Assertions.assertTrue(logic.createFreehold("plot_busy", WORLD_ID, 1000.0, AUTHORITY, null));
        Assertions.assertTrue(logic.createFreehold("plot_elsewhere", OTHER_WORLD, 1000.0, AUTHORITY, null));
        logic.createAuction("plot_quiet", WORLD_ID, AUTHORITY, 3600, 3600, 100.0, 10.0);
        logic.createAuction("plot_busy", WORLD_ID, AUTHORITY, 7200, 3600, 100.0, 10.0);
        logic.createAuction("plot_elsewhere", OTHER_WORLD, AUTHORITY, 3600, 3600, 100.0, 10.0);
    }

    private static List<ActiveAuctionRow> page(UUID worldId, AuctionSort sort, int limit, int offset) {
        try (SqlSessionWrapper session = database.openSession(true)) {
            return session.freeholdContractAuctionMapper().selectActivePage(worldId, sort, limit, offset);
        }
    }

    private static int count(UUID worldId) {
        try (SqlSessionWrapper session = database.openSession(true)) {
            return session.freeholdContractAuctionMapper().countActiveInWorld(worldId);
        }
    }

    private static ActiveAuctionRow rowFor(List<ActiveAuctionRow> rows, String regionId) {
        for (ActiveAuctionRow row : rows) {
            if (row.worldGuardRegionId().equals(regionId)) {
                return row;
            }
        }
        return Assertions.fail("no row for " + regionId + " in " + ids(rows));
    }

    private static List<String> ids(List<ActiveAuctionRow> rows) {
        List<String> ids = new ArrayList<>();
        for (ActiveAuctionRow row : rows) {
            ids.add(row.worldGuardRegionId());
        }
        return ids;
    }

    @Test
    void yieldsARowForAnAuctionNobodyHasBidOn() {
        ActiveAuctionRow row = rowFor(page(null, AuctionSort.ENDING_SOON, 50, 0), "plot_quiet");
        Assertions.assertNull(row.highestBidderId());
        Assertions.assertNull(row.highestBidPrice());
        Assertions.assertNull(row.highestBidTime());
        Assertions.assertEquals(0, row.bidderCount());
        Assertions.assertEquals(100.0, row.minBid());
        Assertions.assertEquals(10.0, row.minStep());
    }

    @Test
    void computesTheDeadlineFromTheStartWhenThereIsNoBid() {
        ActiveAuctionRow row = rowFor(page(null, AuctionSort.ENDING_SOON, 50, 0), "plot_quiet");
        Assertions.assertEquals(row.startDate().plusSeconds(3600), row.endDate());
    }

    @Test
    void carriesTheStandingBidAndItsBidder() {
        Assertions.assertNotNull(logic.performBid("plot_busy", WORLD_ID, BIDDER, 150.0));
        ActiveAuctionRow row = rowFor(page(null, AuctionSort.ENDING_SOON, 50, 0), "plot_busy");
        Assertions.assertEquals(BIDDER, row.highestBidderId());
        Assertions.assertEquals(150.0, row.highestBidPrice());
        Assertions.assertNotNull(row.highestBidTime());
    }

    @Test
    void movesTheDeadlineToTheLastBidPlusTheBiddingDuration() {
        Assertions.assertNotNull(logic.performBid("plot_busy", WORLD_ID, BIDDER, 150.0));
        ActiveAuctionRow row = rowFor(page(null, AuctionSort.ENDING_SOON, 50, 0), "plot_busy");
        Assertions.assertEquals(row.highestBidTime().plusSeconds(7200), row.endDate(),
                "the deadline runs from the last bid, not from the auction start");
    }

    @Test
    void countsDistinctBiddersNotBids() {
        Assertions.assertNotNull(logic.performBid("plot_busy", WORLD_ID, BIDDER, 150.0));
        Assertions.assertNotNull(logic.performBid("plot_busy", WORLD_ID, OTHER_BIDDER, 200.0));
        Assertions.assertNotNull(logic.performBid("plot_busy", WORLD_ID, BIDDER, 250.0));
        ActiveAuctionRow row = rowFor(page(null, AuctionSort.ENDING_SOON, 50, 0), "plot_busy");
        Assertions.assertEquals(2, row.bidderCount());
        Assertions.assertEquals(250.0, row.highestBidPrice(), "the highest bid, not the latest");
    }

    @Test
    void ordersByTheStandingBidUnderHighestBid() {
        Assertions.assertNotNull(logic.performBid("plot_quiet", WORLD_ID, BIDDER, 500.0));
        List<String> ordered = ids(page(WORLD_ID, AuctionSort.HIGHEST_BID, 50, 0));
        Assertions.assertEquals(List.of("plot_quiet", "plot_busy"), ordered,
                "an auction with no bid ranks at its minimum bid, below one bid up to 500");
    }

    @Test
    void ordersBySoonestDeadlineUnderEndingSoon() {
        List<String> ordered = ids(page(WORLD_ID, AuctionSort.ENDING_SOON, 50, 0));
        Assertions.assertEquals(List.of("plot_quiet", "plot_busy"), ordered,
                "plot_quiet closes an hour out, plot_busy two");
    }

    @Test
    void narrowsAndCountsByWorld() {
        Assertions.assertEquals(List.of("plot_elsewhere"),
                ids(page(OTHER_WORLD, AuctionSort.ENDING_SOON, 50, 0)));
        Assertions.assertEquals(2, count(WORLD_ID));
        Assertions.assertEquals(1, count(OTHER_WORLD));
        Assertions.assertEquals(3, count(null));
    }

    @Test
    void excludesAnEndedAuction() {
        Assertions.assertNotNull(logic.cancelAuction("plot_quiet", WORLD_ID));
        Assertions.assertFalse(ids(page(WORLD_ID, AuctionSort.ENDING_SOON, 50, 0)).contains("plot_quiet"));
        Assertions.assertEquals(1, count(WORLD_ID));
    }
}
