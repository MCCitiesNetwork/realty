package io.github.md5sha256.realty.database;

import io.github.md5sha256.realty.api.RealtyBackend;
import io.github.md5sha256.realty.database.entity.PlotOwnerCount;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The ranked, paged plot counts behind {@code /v1/leaderboard/owners}.
 */
class OwnersLeaderboardQueryTest extends AbstractDatabaseTest {

    private static final UUID WORLD_ID = UUID.fromString("8f4d1c2e-0000-0000-0000-000000000041");
    private static final UUID AUTHORITY = UUID.fromString("3a1c88f0-0000-0000-0000-000000000040");
    private static final UUID ALICE = UUID.fromString("aaaa0000-0000-0000-0000-000000000001");
    private static final UUID BOB = UUID.fromString("bbbb0000-0000-0000-0000-000000000002");
    private static final UUID CAROL = UUID.fromString("cccc0000-0000-0000-0000-000000000003");

    @BeforeEach
    void seed() {
        for (int i = 0; i < 3; i++) {
            Assertions.assertTrue(logic.createFreehold("alice_" + i, WORLD_ID, null, AUTHORITY, ALICE));
        }
        Assertions.assertTrue(logic.createFreehold("bob_0", WORLD_ID, null, AUTHORITY, BOB));
        Assertions.assertTrue(logic.createFreehold("carol_0", WORLD_ID, null, AUTHORITY, CAROL));
        // Unsold: it has no title holder, so nobody should be credited with it.
        Assertions.assertTrue(logic.createFreehold("unsold", WORLD_ID, 100.0, AUTHORITY, null));
    }

    private static List<PlotOwnerCount> page(int limit, int offset) {
        try (SqlSessionWrapper session = database.openSession(true)) {
            return session.freeholdContractMapper().selectPlotCountsByTitleHolderPaged(limit, offset);
        }
    }

    private static int distinctOwners() {
        try (SqlSessionWrapper session = database.openSession(true)) {
            return session.freeholdContractMapper().countDistinctTitleHolders();
        }
    }

    @Test
    void ranksOwnersByPlotCountHighestFirst() {
        List<PlotOwnerCount> rows = page(50, 0);
        Assertions.assertEquals(3, rows.size());
        Assertions.assertEquals(ALICE, rows.get(0).titleHolderId());
        Assertions.assertEquals(3, rows.get(0).plotCount());
    }

    @Test
    void excludesPlotsNobodyHolds() {
        int total = 0;
        for (PlotOwnerCount row : page(50, 0)) {
            total += row.plotCount();
        }
        Assertions.assertEquals(5, total, "the unsold plot must not be credited to anyone");
        Assertions.assertEquals(3, distinctOwners());
    }

    @Test
    void breaksTiesInATotalOrderSoPagingNeitherRepeatsNorSkips() {
        // Bob and Carol both hold one plot: without the titleHolderId tiebreak the
        // database is free to order them differently between the two page queries.
        List<UUID> paged = new ArrayList<>();
        for (int offset = 0; offset < 3; offset++) {
            for (PlotOwnerCount row : page(1, offset)) {
                paged.add(row.titleHolderId());
            }
        }
        List<UUID> whole = new ArrayList<>();
        for (PlotOwnerCount row : page(50, 0)) {
            whole.add(row.titleHolderId());
        }
        Assertions.assertEquals(whole, paged);
        Assertions.assertEquals(3, paged.stream().distinct().count());
    }

    @Test
    void countsDistinctHoldersNotContracts() {
        Assertions.assertEquals(3, distinctOwners(),
                "Alice's three plots are one holder, not three");
    }

    @Test
    void dropsAnOwnerWhoLosesTheirLastTitle() {
        Assertions.assertInstanceOf(RealtyBackend.SetTitleHolderResult.Success.class,
                logic.setTitleHolder("bob_0", WORLD_ID, null));
        List<UUID> holders = new ArrayList<>();
        for (PlotOwnerCount row : page(50, 0)) {
            holders.add(row.titleHolderId());
        }
        Assertions.assertFalse(holders.contains(BOB),
                "a plot with no title holder credits nobody");
        Assertions.assertEquals(2, distinctOwners());
    }

    @Test
    void reportsNobodyOnAnEstateWhereNothingIsHeld() {
        for (int i = 0; i < 3; i++) {
            Assertions.assertInstanceOf(RealtyBackend.SetTitleHolderResult.Success.class,
                    logic.setTitleHolder("alice_" + i, WORLD_ID, null));
        }
        Assertions.assertInstanceOf(RealtyBackend.SetTitleHolderResult.Success.class,
                logic.setTitleHolder("bob_0", WORLD_ID, null));
        Assertions.assertInstanceOf(RealtyBackend.SetTitleHolderResult.Success.class,
                logic.setTitleHolder("carol_0", WORLD_ID, null));
        Assertions.assertEquals(0, distinctOwners());
        Assertions.assertTrue(page(50, 0).isEmpty());
    }
}
