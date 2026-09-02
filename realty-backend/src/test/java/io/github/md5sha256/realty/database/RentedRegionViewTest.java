package io.github.md5sha256.realty.database;

import io.github.md5sha256.realty.database.entity.RentedRegionView;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

class RentedRegionViewTest extends AbstractDatabaseTest {

    private static final UUID WORLD_ID = UUID.fromString("8f4d1c2e-0000-0000-0000-000000000002");
    private static final UUID TENANT = UUID.fromString("3a1c88f0-0000-0000-0000-000000000001");
    private static final UUID LANDLORD = UUID.fromString("3a1c88f0-0000-0000-0000-000000000002");

    /**
     * There is no public {@code RealtyBackend} method to set an arbitrary leasehold end date
     * directly (only relative adjustments via renew/rollback, or {@code scheduleTermination}
     * which also stamps termination fields we do not want here), so the test sets it directly
     * against the table.
     */
    private static void setEndDate(String worldGuardRegionId, LocalDateTime endDate) throws SQLException {
        try (Connection conn = DriverManager.getConnection(CONTAINER.getJdbcUrl(), CONTAINER.getUsername(), CONTAINER.getPassword());
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("""
                    UPDATE LeaseholdContract lc
                    INNER JOIN Contract c ON c.contractId = lc.leaseholdContractId AND c.contractType = 'leasehold'
                    INNER JOIN RealtyRegion rr ON rr.realtyRegionId = c.realtyRegionId
                    SET lc.endDate = '%s'
                    WHERE rr.worldGuardRegionId = '%s'
                    """.formatted(endDate, worldGuardRegionId));
        }
    }

    @Test
    void returnsOneRowPerRentedRegionCarryingItsEndDate() throws SQLException {
        LocalDateTime end = LocalDateTime.of(2026, 10, 1, 12, 0, 0);
        logic.createLeasehold("plot_rented", WORLD_ID, 100.0, 604800L, -1, LANDLORD);
        logic.setTenant("plot_rented", WORLD_ID, TENANT);
        setEndDate("plot_rented", end);

        try (SqlSessionWrapper session = database.openSession(true)) {
            List<RentedRegionView> rows = session.leaseholdContractMapper()
                    .selectRentedRegionsWithEndDate(TENANT, 10, 0);
            Assertions.assertEquals(1, rows.size());
            Assertions.assertEquals("plot_rented", rows.getFirst().worldGuardRegionId());
            Assertions.assertEquals(end, rows.getFirst().endDate());
        }
    }

    @Test
    void toleratesANullEndDate() {
        logic.createLeasehold("plot_no_end", WORLD_ID, 100.0, 604800L, -1, LANDLORD);
        logic.setTenant("plot_no_end", WORLD_ID, TENANT);

        try (SqlSessionWrapper session = database.openSession(true)) {
            List<RentedRegionView> rows = session.leaseholdContractMapper()
                    .selectRentedRegionsWithEndDate(TENANT, 10, 0);
            Assertions.assertEquals(1, rows.size());
            Assertions.assertNull(rows.getFirst().endDate());
        }
    }

    @Test
    void returnsNothingForATenantWhoRentsNothing() {
        try (SqlSessionWrapper session = database.openSession(true)) {
            List<RentedRegionView> rows = session.leaseholdContractMapper()
                    .selectRentedRegionsWithEndDate(UUID.randomUUID(), 10, 0);
            Assertions.assertTrue(rows.isEmpty());
        }
    }
}
