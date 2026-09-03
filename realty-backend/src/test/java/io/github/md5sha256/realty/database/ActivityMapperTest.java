package io.github.md5sha256.realty.database;

import io.github.md5sha256.realty.database.entity.ActivityRow;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The three-table union behind {@code /v1/activity}.
 *
 * <p>Two things here can only be settled against a real database: that the typed NULL
 * padding lets the three branches union at all, and that ordering and paging applied
 * outside the union really do produce one interleaved feed rather than three
 * separately-paged ones.</p>
 */
class ActivityMapperTest extends AbstractDatabaseTest {

    private static final UUID WORLD_ID = UUID.fromString("8f4d1c2e-0000-0000-0000-000000000061");
    private static final UUID OTHER_WORLD = UUID.fromString("8f4d1c2e-0000-0000-0000-000000000062");
    private static final UUID ALICE = UUID.fromString("aaaa0000-0000-0000-0000-000000000011");
    private static final UUID BOB = UUID.fromString("bbbb0000-0000-0000-0000-000000000012");

    private static final List<String> TICKER =
            List.of("BUY", "AUCTION_BUY", "OFFER_BUY", "RENT");

    @BeforeEach
    void seed() {
        try (SqlSessionWrapper session = database.openSession(true)) {
            session.freeholdHistoryMapper().insert("plot_a", WORLD_ID, "BUY", ALICE, BOB, 21500.0);
            session.leaseholdHistoryMapper().insert("plot_b", WORLD_ID, "RENT", ALICE, BOB,
                    800.0, 604800L, 3);
            session.agentHistoryMapper().insert("plot_c", WORLD_ID, "AGENT_ADD", ALICE, BOB);
            session.freeholdHistoryMapper().insert("plot_d", WORLD_ID, "SET_PRICE", ALICE, BOB, 100.0);
            session.freeholdHistoryMapper().insert("plot_e", OTHER_WORLD, "BUY", ALICE, BOB, 50.0);
        }
    }

    private static List<ActivityRow> page(List<String> types, UUID worldId, LocalDateTime since,
                                          int limit, int offset) {
        try (SqlSessionWrapper session = database.openSession(true)) {
            return session.activityMapper().selectPage(types, worldId, since, limit, offset);
        }
    }

    private static int count(List<String> types, UUID worldId, LocalDateTime since) {
        try (SqlSessionWrapper session = database.openSession(true)) {
            return session.activityMapper().countMatching(types, worldId, since);
        }
    }

    private static List<String> ids(List<ActivityRow> rows) {
        List<String> ids = new ArrayList<>();
        for (ActivityRow row : rows) {
            ids.add(row.worldGuardRegionId());
        }
        return ids;
    }

    private static ActivityRow rowFor(List<ActivityRow> rows, String regionId) {
        for (ActivityRow row : rows) {
            if (row.worldGuardRegionId().equals(regionId)) {
                return row;
            }
        }
        return Assertions.fail("no row for " + regionId + " in " + ids(rows));
    }

    @Test
    void unionsAllThreeTablesIntoOneFeed() {
        List<String> all = new ArrayList<>(TICKER);
        all.add("AGENT_ADD");
        all.add("SET_PRICE");
        List<ActivityRow> rows = page(all, null, null, 50, 0);
        Assertions.assertEquals(5, rows.size());
        Assertions.assertEquals(5, count(all, null, null));
    }

    @Test
    void discriminatesEachBranchByKind() {
        List<String> all = List.of("BUY", "RENT", "AGENT_ADD");
        List<ActivityRow> rows = page(all, null, null, 50, 0);
        Assertions.assertEquals("freehold", rowFor(rows, "plot_a").kind());
        Assertions.assertEquals("leasehold", rowFor(rows, "plot_b").kind());
        Assertions.assertEquals("agent", rowFor(rows, "plot_c").kind());
    }

    @Test
    void carriesTheColumnsEachBranchHasAndNullsTheRest() {
        List<ActivityRow> rows = page(List.of("BUY", "RENT", "AGENT_ADD"), null, null, 50, 0);

        ActivityRow freehold = rowFor(rows, "plot_a");
        Assertions.assertEquals(21500.0, freehold.price());
        Assertions.assertNull(freehold.durationSeconds(), "a freehold event has no lease term");
        Assertions.assertNull(freehold.extensionsRemaining());

        ActivityRow lease = rowFor(rows, "plot_b");
        Assertions.assertEquals(800.0, lease.price());
        Assertions.assertEquals(604800L, lease.durationSeconds());
        Assertions.assertEquals(3, lease.extensionsRemaining());

        ActivityRow agent = rowFor(rows, "plot_c");
        Assertions.assertNull(agent.price(), "an agent event has no price");
        Assertions.assertNull(agent.durationSeconds());
    }

    @Test
    void readsTheTwoPlayerColumnsPositionallyPerBranch() {
        List<ActivityRow> rows = page(List.of("BUY", "RENT", "AGENT_ADD"), null, null, 50, 0);
        for (String regionId : List.of("plot_a", "plot_b", "plot_c")) {
            ActivityRow row = rowFor(rows, regionId);
            Assertions.assertEquals(ALICE, row.firstPlayerId(), regionId);
            Assertions.assertEquals(BOB, row.secondPlayerId(), regionId);
        }
    }

    @Test
    void narrowsToTheRequestedEventTypes() {
        Assertions.assertEquals(List.of("plot_d"),
                ids(page(List.of("SET_PRICE"), null, null, 50, 0)));
        Assertions.assertEquals(1, count(List.of("SET_PRICE"), null, null));
    }

    @Test
    void excludesAuditEventsFromTheDefaultTickerSet() {
        List<String> ids = ids(page(TICKER, null, null, 50, 0));
        Assertions.assertFalse(ids.contains("plot_d"), "SET_PRICE is not a ticker event");
        Assertions.assertFalse(ids.contains("plot_c"), "AGENT_ADD is not a ticker event");
        Assertions.assertEquals(3, count(TICKER, null, null));
    }

    @Test
    void narrowsToOneWorldAcrossEveryBranch() {
        Assertions.assertEquals(List.of("plot_e"),
                ids(page(TICKER, OTHER_WORLD, null, 50, 0)));
        Assertions.assertEquals(1, count(TICKER, OTHER_WORLD, null));
        Assertions.assertEquals(2, count(TICKER, WORLD_ID, null));
    }

    @Test
    void appliesSinceAcrossEveryBranch() {
        LocalDateTime future = LocalDateTime.now().plusDays(1);
        Assertions.assertTrue(page(TICKER, null, future, 50, 0).isEmpty());
        Assertions.assertEquals(0, count(TICKER, null, future));

        LocalDateTime past = LocalDateTime.now().minusDays(1);
        Assertions.assertEquals(3, count(TICKER, null, past));
    }

    @Test
    void pagesTheCombinedFeedRatherThanEachTableSeparately() {
        List<String> all = List.of("BUY", "RENT", "AGENT_ADD", "SET_PRICE");
        List<String> whole = ids(page(all, null, null, 50, 0));
        List<String> paged = new ArrayList<>();
        for (int offset = 0; offset < whole.size(); offset++) {
            paged.addAll(ids(page(all, null, null, 1, offset)));
        }
        Assertions.assertEquals(whole, paged,
                "one row at a time must walk the same feed the full page reports");
        Assertions.assertEquals(whole.size(), paged.stream().distinct().count(),
                "three separately-paged tables would repeat rows here");
    }
}
