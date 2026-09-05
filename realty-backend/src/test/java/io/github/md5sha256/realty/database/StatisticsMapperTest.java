package io.github.md5sha256.realty.database;

import io.github.md5sha256.realty.database.entity.StatisticsEntity;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.UUID;

/**
 * The single statement must answer exactly what the ten standalone counters answer,
 * since the API reports the one and the in-game commands still use the others.
 */
class StatisticsMapperTest extends AbstractDatabaseTest {

    private static final UUID WORLD_ID = UUID.fromString("8f4d1c2e-0000-0000-0000-000000000004");
    private static final UUID AUTHORITY = UUID.fromString("3a1c88f0-0000-0000-0000-000000000020");
    private static final UUID OWNER = UUID.fromString("3a1c88f0-0000-0000-0000-000000000021");
    private static final UUID LANDLORD = UUID.fromString("3a1c88f0-0000-0000-0000-000000000022");

    private static StatisticsEntity statistics() {
        try (SqlSessionWrapper session = database.openSession(true)) {
            return session.statisticsMapper().select();
        }
    }

    @Test
    void anEmptyEstateReportsZeroesRatherThanNulls() {
        StatisticsEntity stats = statistics();
        Assertions.assertEquals(0, stats.regions());
        Assertions.assertEquals(0.0, stats.averageFreeholdPrice());
        Assertions.assertEquals(0L, stats.averageLeaseholdDurationSeconds());
    }

    @Test
    void agreesWithEveryStandaloneCounter() {
        Assertions.assertTrue(logic.createFreehold("stats_listed", WORLD_ID, 5000.0, AUTHORITY, null));
        Assertions.assertTrue(logic.createFreehold("stats_sold", WORLD_ID, null, AUTHORITY, OWNER));
        Assertions.assertTrue(logic.createLeasehold("stats_rental", WORLD_ID, 250.0, 604800L, -1, LANDLORD));

        StatisticsEntity stats = statistics();

        Assertions.assertEquals(logic.countAllRegions(), stats.regions());
        Assertions.assertEquals(logic.countAllFreeholdContracts(), stats.freeholdContracts());
        Assertions.assertEquals(logic.countOccupiedFreeholdContracts(), stats.occupiedFreeholds());
        Assertions.assertEquals(logic.averageFreeholdPrice(), stats.averageFreeholdPrice());
        Assertions.assertEquals(logic.countAllLeaseholdContracts(), stats.leaseholdContracts());
        Assertions.assertEquals(logic.countOccupiedLeaseholdContracts(), stats.occupiedLeaseholds());
        Assertions.assertEquals(logic.averageLeaseholdPrice(), stats.averageLeaseholdPrice());
        Assertions.assertEquals(logic.averageLeaseholdDurationSeconds(), stats.averageLeaseholdDurationSeconds());
        Assertions.assertEquals(logic.countActiveOffers(), stats.activeOffers());
        Assertions.assertEquals(logic.countActiveAuctions(), stats.activeAuctions());

        // And the figures themselves, so a counter that is wrong in the same way twice
        // does not pass by agreeing with itself.
        Assertions.assertEquals(3, stats.regions());
        Assertions.assertEquals(2, stats.freeholdContracts());
        Assertions.assertEquals(1, stats.occupiedFreeholds());
        Assertions.assertEquals(5000.0, stats.averageFreeholdPrice());
        Assertions.assertEquals(1, stats.leaseholdContracts());
        Assertions.assertEquals(250.0, stats.averageLeaseholdPrice());
        // The mean spans the leases currently let, and none is: nobody rents plot_rental.
        Assertions.assertEquals(0L, stats.averageLeaseholdDurationSeconds());
    }
}
