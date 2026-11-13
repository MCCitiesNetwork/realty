package io.github.md5sha256.realty.database;

import io.github.md5sha256.realty.database.maria.MariaSchemaMigrator;
import io.github.md5sha256.realty.database.migration.MigrationStep;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Migration 23 ties each lease and freehold row to its region, so it is tested as the upgrade it
 * is: a database at version 22 holding live contracts and contract rows a region delete left
 * behind, then migrated.
 */
class ContractCascadeMigrationTest extends AbstractDatabaseTest {

    private static final String DATABASE = "contract_cascade";
    private static final String ROOT_PASSWORD = "rootpass";
    private static final UUID WORLD_ID = UUID.fromString("8f4d1c2e-0000-0000-0000-0000000000d1");
    private static final UUID OWNER = UUID.fromString("3a1c88f0-0000-0000-0000-0000000000d1");

    @Test
    void leftoverRowsGoAndLiveRowsCascadeWithTheirRegion() throws Exception {
        String containerUrl = CONTAINER.getJdbcUrl();
        String freshUrl = containerUrl.substring(0, containerUrl.lastIndexOf('/') + 1) + DATABASE;
        try (Connection root = DriverManager.getConnection(containerUrl, "root", ROOT_PASSWORD);
             Statement statement = root.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + DATABASE);
            statement.execute("CREATE DATABASE " + DATABASE);
        }
        List<MigrationStep> all = MariaSchemaMigrator.defaultMigrations();
        MariaSchemaMigrator.migrate(freshUrl, "root", ROOT_PASSWORD, Path.of("sql/migrations"),
                all.stream().filter(step -> step.version() <= 22).toList(), Logger.getLogger("test"));

        int leaseRegion;
        int liveLease;
        int liveFreehold;
        int leftoverLease;
        int leftoverFreehold;
        try (Connection connection = DriverManager.getConnection(freshUrl, "root", ROOT_PASSWORD);
             Statement statement = connection.createStatement()) {
            int partyId = fetchInt(statement, "INSERT INTO Party (kind) VALUES ('PERSONAL') RETURNING partyId");
            statement.executeUpdate("INSERT INTO PersonalParty (partyId, playerUuid) VALUES (%d, '%s')"
                    .formatted(partyId, OWNER));

            leaseRegion = insertRegion(statement, "plot_lease");
            liveLease = insertLease(statement, partyId);
            link(statement, liveLease, "leasehold", leaseRegion);
            int freeholdRegion = insertRegion(statement, "plot_freehold");
            liveFreehold = insertFreehold(statement, partyId);
            link(statement, liveFreehold, "freehold", freeholdRegion);

            // What /realty delete left before this migration: the region and its Contract link gone,
            // the contract row (and a lease's modification) still there.
            int deletedRegion = insertRegion(statement, "plot_deleted");
            leftoverLease = insertLease(statement, partyId);
            link(statement, leftoverLease, "leasehold", deletedRegion);
            statement.executeUpdate("""
                    INSERT INTO LeaseholdModification (leaseholdContractId, proposerRole, proposerId, status, newPrice)
                    VALUES (%d, 'landlord', '%s', 'AWAITING_LANDLORD', 50.0)
                    """.formatted(leftoverLease, OWNER));
            leftoverFreehold = insertFreehold(statement, partyId);
            link(statement, leftoverFreehold, "freehold", deletedRegion);
            statement.executeUpdate("DELETE FROM RealtyRegion WHERE realtyRegionId = " + deletedRegion);
        }

        MariaSchemaMigrator.migrate(freshUrl, "root", ROOT_PASSWORD, Path.of("sql/migrations"),
                all.stream().filter(step -> step.version() <= 23).toList(), Logger.getLogger("test"));

        try (Connection connection = DriverManager.getConnection(freshUrl, "root", ROOT_PASSWORD);
             Statement statement = connection.createStatement()) {
            Assertions.assertEquals(0, count(statement, "LeaseholdContract WHERE leaseholdContractId = " + leftoverLease));
            Assertions.assertEquals(0, count(statement, "FreeholdContract WHERE freeholdContractId = " + leftoverFreehold));
            Assertions.assertEquals(0, count(statement, "LeaseholdModification WHERE leaseholdContractId = " + leftoverLease));
            Assertions.assertEquals(leaseRegion, fetchInt(statement,
                    "SELECT realtyRegionId FROM LeaseholdContract WHERE leaseholdContractId = " + liveLease));
            Assertions.assertEquals(1, count(statement, "FreeholdContract WHERE freeholdContractId = " + liveFreehold));

            statement.executeUpdate("DELETE FROM RealtyRegion WHERE realtyRegionId = " + leaseRegion);
            Assertions.assertEquals(0, count(statement, "LeaseholdContract WHERE leaseholdContractId = " + liveLease),
                    "the lease row now goes with its region");
        }
    }

    private static int insertRegion(Statement statement, String worldGuardRegionId) throws SQLException {
        return fetchInt(statement, """
                INSERT INTO RealtyRegion (worldGuardRegionId, worldId) VALUES ('%s', '%s')
                RETURNING realtyRegionId
                """.formatted(worldGuardRegionId, WORLD_ID));
    }

    private static int insertLease(Statement statement, int landlordPartyId) throws SQLException {
        return fetchInt(statement, """
                INSERT INTO LeaseholdContract (landlordPartyId, price, durationSeconds)
                VALUES (%d, 100.0, 3600)
                RETURNING leaseholdContractId
                """.formatted(landlordPartyId));
    }

    private static int insertFreehold(Statement statement, int authorityPartyId) throws SQLException {
        return fetchInt(statement, """
                INSERT INTO FreeholdContract (authorityPartyId) VALUES (%d)
                RETURNING freeholdContractId
                """.formatted(authorityPartyId));
    }

    private static void link(Statement statement, int contractId, String type, int realtyRegionId)
            throws SQLException {
        statement.executeUpdate("INSERT INTO Contract (contractId, contractType, realtyRegionId) VALUES (%d, '%s', %d)"
                .formatted(contractId, type, realtyRegionId));
    }

    private static int count(Statement statement, String fromWhere) throws SQLException {
        return fetchInt(statement, "SELECT COUNT(*) FROM " + fromWhere);
    }

    private static int fetchInt(Statement statement, String sql) throws SQLException {
        try (ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
