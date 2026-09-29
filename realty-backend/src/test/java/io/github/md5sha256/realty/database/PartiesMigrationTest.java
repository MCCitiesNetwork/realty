package io.github.md5sha256.realty.database;

import io.github.md5sha256.realty.database.maria.MariaSchemaMigrator;
import io.github.md5sha256.realty.database.migration.MigrationStep;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Migration 19 replaces the bare UUID columns {@code landlordId}/{@code authorityId} with a
 * {@code Party} row, so it is tested the same way migration 18 is: a database stopped at the
 * version before it, seeded with the old shape, then migrated across.
 *
 * <p>This builds a database of its own in the same container (as
 * {@link PurgeWorldEditSchematicsMigrationTest} does), seeded once in {@link #migrateAndSeed()},
 * so every {@code @Test} below reads the one migrated database. A test that inserts or updates
 * rows against it rolls its own change back, so it never changes what another test observes.</p>
 */
class PartiesMigrationTest extends AbstractDatabaseTest {

    private static final String DATABASE = "parties_migration";
    private static final String ROOT_PASSWORD = "rootpass";

    private static final UUID WORLD_ID = UUID.fromString("8f4d1c2e-0000-0000-0000-0000000000a1");

    // Player A: landlord of a leasehold, and authority of a freehold. One Party row covers both.
    private static final UUID LANDLORD_AND_AUTHORITY = UUID.fromString("3a1c88f0-0000-0000-0000-0000000000a1");
    // Player B: landlord on a history row only, never on a current contract.
    private static final UUID HISTORY_LANDLORD = UUID.fromString("3a1c88f0-0000-0000-0000-0000000000a2");
    private static final UUID HISTORY_TENANT = UUID.fromString("3a1c88f0-0000-0000-0000-0000000000a3");

    private static String freshUrl;

    @BeforeAll
    static void migrateAndSeed() throws Exception {
        String containerUrl = CONTAINER.getJdbcUrl();
        freshUrl = containerUrl.substring(0, containerUrl.lastIndexOf('/') + 1) + DATABASE;
        try (Connection root = DriverManager.getConnection(containerUrl, "root", ROOT_PASSWORD);
             Statement statement = root.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + DATABASE);
            statement.execute("CREATE DATABASE " + DATABASE);
        }

        List<MigrationStep> all = MariaSchemaMigrator.defaultMigrations();
        List<MigrationStep> upToEighteen = all.stream().filter(step -> step.version() <= 18).toList();
        MariaSchemaMigrator.migrate(freshUrl, "root", ROOT_PASSWORD,
                Path.of("sql/migrations"), upToEighteen, Logger.getLogger("test"));

        try (Connection connection = DriverManager.getConnection(freshUrl, "root", ROOT_PASSWORD);
             Statement statement = connection.createStatement()) {
            int leaseRegionId = insertRegion(statement, "plot_lease");
            int freeholdRegionId = insertRegion(statement, "plot_freehold");

            int leaseholdContractId = fetchGeneratedId(statement, """
                    INSERT INTO LeaseholdContract (landlordId, price, durationSeconds)
                    VALUES ('%s', 100.0, 604800)
                    RETURNING leaseholdContractId
                    """.formatted(LANDLORD_AND_AUTHORITY));
            statement.executeUpdate("""
                    INSERT INTO Contract (contractId, contractType, realtyRegionId)
                    VALUES (%d, 'leasehold', %d)
                    """.formatted(leaseholdContractId, leaseRegionId));

            int freeholdContractId = fetchGeneratedId(statement, """
                    INSERT INTO FreeholdContract (authorityId)
                    VALUES ('%s')
                    RETURNING freeholdContractId
                    """.formatted(LANDLORD_AND_AUTHORITY));
            statement.executeUpdate("""
                    INSERT INTO Contract (contractId, contractType, realtyRegionId)
                    VALUES (%d, 'freehold', %d)
                    """.formatted(freeholdContractId, freeholdRegionId));

            statement.executeUpdate("""
                    INSERT INTO LeaseholdHistory (worldGuardRegionId, worldId, eventType, tenantId, landlordId, price, durationSeconds)
                    VALUES ('plot_lease', '%s', 'RENT', '%s', '%s', 50.0, 604800)
                    """.formatted(WORLD_ID, HISTORY_TENANT, HISTORY_LANDLORD));
            statement.executeUpdate("""
                    INSERT INTO FreeholdHistory (worldGuardRegionId, worldId, eventType, buyerId, authorityId, price)
                    VALUES ('plot_freehold', '%s', 'BUY', '%s', '%s', 900.0)
                    """.formatted(WORLD_ID, HISTORY_TENANT, LANDLORD_AND_AUTHORITY));
        }

        MariaSchemaMigrator.migrate(freshUrl, "root", ROOT_PASSWORD,
                Path.of("sql/migrations"), all, Logger.getLogger("test"));
    }

    private static int insertRegion(Statement statement, String worldGuardRegionId) throws SQLException {
        return fetchGeneratedId(statement, """
                INSERT INTO RealtyRegion (worldGuardRegionId, worldId)
                VALUES ('%s', '%s')
                RETURNING realtyRegionId
                """.formatted(worldGuardRegionId, WORLD_ID));
    }

    private static int fetchGeneratedId(Statement statement, String sql) throws SQLException {
        try (ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    @Test
    void everyOldUuidBecomesOnePersonalParty() throws SQLException {
        try (Connection connection = DriverManager.getConnection(freshUrl, "root", ROOT_PASSWORD);
             Statement statement = connection.createStatement()) {
            Assertions.assertEquals(2, count(statement, "SELECT COUNT(*) FROM Party WHERE kind = 'PERSONAL'"),
                    "player A (landlord + authority) and player B (history landlord) should collapse to two PERSONAL parties");
            Assertions.assertEquals(2, count(statement, "SELECT COUNT(*) FROM Party"),
                    "no non-PERSONAL party should exist from the migration alone");
            Assertions.assertEquals(2, count(statement, """
                    SELECT COUNT(*) FROM Party p JOIN PersonalParty pp ON pp.partyId = p.partyId
                    """), "every base row should have its PersonalParty row");
            Assertions.assertEquals(0, count(statement, "SELECT COUNT(*) FROM AccountParty"));
            Assertions.assertEquals(0, count(statement, "SELECT COUNT(*) FROM GroupParty"));
        }
    }

    private static int count(Statement statement, String sql) throws SQLException {
        try (ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    @Test
    void repointedRowsMatchTheirOriginals() throws SQLException {
        try (Connection connection = DriverManager.getConnection(freshUrl, "root", ROOT_PASSWORD)) {
            assertPartyUuid(connection, """
                    SELECT p.playerUuid FROM LeaseholdContract lc
                    JOIN PersonalParty p ON p.partyId = lc.landlordPartyId
                    """, LANDLORD_AND_AUTHORITY, "leasehold landlord");
            assertPartyUuid(connection, """
                    SELECT p.playerUuid FROM FreeholdContract fc
                    JOIN PersonalParty p ON p.partyId = fc.authorityPartyId
                    """, LANDLORD_AND_AUTHORITY, "freehold authority");
            assertPartyUuid(connection, """
                    SELECT p.playerUuid FROM LeaseholdHistory lh
                    JOIN PersonalParty p ON p.partyId = lh.landlordPartyId
                    """, HISTORY_LANDLORD, "leasehold history landlord");
        }
    }

    private static void assertPartyUuid(Connection connection, String sql, UUID expected, String label) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            Assertions.assertTrue(rs.next(), "expected a row for " + label);
            Assertions.assertEquals(expected, rs.getObject(1, UUID.class), "wrong player behind the " + label + " party");
        }
    }

    @Test
    void newPartyColumnsAreNotNull() throws SQLException {
        try (Connection connection = DriverManager.getConnection(freshUrl, "root", ROOT_PASSWORD);
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("""
                     SELECT table_name, column_name, is_nullable FROM information_schema.columns
                     WHERE table_schema = DATABASE()
                     AND ((table_name = 'LeaseholdContract' AND column_name = 'landlordPartyId')
                      OR (table_name = 'LeaseholdHistory' AND column_name = 'landlordPartyId')
                      OR (table_name = 'FreeholdContract' AND column_name = 'authorityPartyId')
                      OR (table_name = 'FreeholdHistory' AND column_name = 'authorityPartyId'))
                     """)) {
            int columns = 0;
            while (rs.next()) {
                columns++;
                Assertions.assertEquals("NO", rs.getString("is_nullable"),
                        rs.getString("table_name") + "." + rs.getString("column_name") + " should be NOT NULL");
            }
            Assertions.assertEquals(4, columns, "all four party columns should exist");
        }
    }

    /**
     * Removing a group mapping relies on these keys: the database itself refuses to delete a
     * party that a contract or a history row still names.
     */
    @Test
    void everyPartyColumnHasAForeignKey() throws SQLException {
        try (Connection connection = DriverManager.getConnection(freshUrl, "root", ROOT_PASSWORD);
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("""
                     SELECT constraint_name, table_name, referenced_table_name
                     FROM information_schema.referential_constraints
                     WHERE constraint_schema = DATABASE()
                     AND constraint_name IN ('fk_leasehold_contract_landlord', 'fk_leasehold_history_landlord',
                                             'fk_freehold_contract_authority', 'fk_freehold_history_authority')
                     ORDER BY constraint_name
                     """)) {
            List<String> found = new ArrayList<>();
            while (rs.next()) {
                Assertions.assertEquals("Party", rs.getString("referenced_table_name"),
                        rs.getString("constraint_name") + " should reference Party");
                found.add(rs.getString("constraint_name") + " on " + rs.getString("table_name"));
            }
            Assertions.assertEquals(List.of(
                    "fk_freehold_contract_authority on FreeholdContract",
                    "fk_freehold_history_authority on FreeholdHistory",
                    "fk_leasehold_contract_landlord on LeaseholdContract",
                    "fk_leasehold_history_landlord on LeaseholdHistory"), found);
        }
    }

    /**
     * A production database may lack an index that a fresh one has. Each index V19 drops sits
     * on a column that V19 drops too, so its absence must not stop the migration part-way.
     */
    @Test
    void missingOldIndex_doesNotStopTheMigration() throws Exception {
        String database = "parties_migration_no_index";
        String containerUrl = CONTAINER.getJdbcUrl();
        String url = containerUrl.substring(0, containerUrl.lastIndexOf('/') + 1) + database;
        try (Connection root = DriverManager.getConnection(containerUrl, "root", ROOT_PASSWORD);
             Statement statement = root.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + database);
            statement.execute("CREATE DATABASE " + database);
        }
        List<MigrationStep> all = MariaSchemaMigrator.defaultMigrations();
        MariaSchemaMigrator.migrate(url, "root", ROOT_PASSWORD, Path.of("sql/migrations"),
                all.stream().filter(step -> step.version() <= 18).toList(), Logger.getLogger("test"));
        try (Connection connection = DriverManager.getConnection(url, "root", ROOT_PASSWORD);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP INDEX idx_leasehold_contract_landlord ON LeaseholdContract");
        }

        MariaSchemaMigrator.migrate(url, "root", ROOT_PASSWORD, Path.of("sql/migrations"),
                all, Logger.getLogger("test"));

        try (Connection connection = DriverManager.getConnection(url, "root", ROOT_PASSWORD);
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT MAX(version) FROM schema_version")) {
            rs.next();
            Assertions.assertEquals(19, rs.getInt(1), "V19 should have been applied");
        }
    }

    @Test
    void oldColumnsAndIndexesAreGone() throws SQLException {
        try (Connection connection = DriverManager.getConnection(freshUrl, "root", ROOT_PASSWORD)) {
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("""
                         SELECT COUNT(*) FROM information_schema.columns
                         WHERE table_schema = DATABASE()
                         AND ((table_name = 'LeaseholdContract' AND column_name = 'landlordId')
                          OR (table_name = 'LeaseholdHistory' AND column_name = 'landlordId')
                          OR (table_name = 'FreeholdContract' AND column_name = 'authorityId')
                          OR (table_name = 'FreeholdHistory' AND column_name = 'authorityId'))
                         """)) {
                rs.next();
                Assertions.assertEquals(0, rs.getInt(1), "the old UUID columns should be gone");
            }
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("""
                         SELECT COUNT(*) FROM information_schema.statistics
                         WHERE table_schema = DATABASE()
                         AND index_name IN ('idx_leasehold_contract_landlord', 'idx_freehold_contract_authority')
                         """)) {
                rs.next();
                Assertions.assertEquals(0, rs.getInt(1), "the old indexes on the dropped columns should be gone");
            }
        }
    }

    @Test
    void historyTenantMayBeNull() throws SQLException {
        try (Connection connection = DriverManager.getConnection(freshUrl, "root", ROOT_PASSWORD)) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                int updated = statement.executeUpdate("UPDATE LeaseholdHistory SET tenantId = NULL");
                Assertions.assertEquals(1, updated);
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    void freeholdHistoryBuyerMayBeNull() throws SQLException {
        try (Connection connection = DriverManager.getConnection(freshUrl, "root", ROOT_PASSWORD)) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                int updated = statement.executeUpdate("UPDATE FreeholdHistory SET buyerId = NULL");
                Assertions.assertEquals(1, updated);
            } finally {
                connection.rollback();
            }
        }
    }

    /** The composite (partyId, kind) key: a kind row can only attach to a base row of its own kind. */
    @Test
    void aKindRowCannotAttachToABaseRowOfAnotherKind() throws SQLException {
        try (Connection connection = DriverManager.getConnection(freshUrl, "root", ROOT_PASSWORD)) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                int personalId = fetchGeneratedId(statement, "INSERT INTO Party (kind) VALUES ('PERSONAL') RETURNING partyId");
                assertRejected(statement, """
                        INSERT INTO AccountParty (partyId, accountId, accountKind) VALUES (%d, 42, 'GOVERNMENT')
                        """.formatted(personalId), "an AccountParty row on a PERSONAL base row");
                assertRejected(statement, """
                        INSERT INTO GroupParty (partyId, groupName, accountPartyId) VALUES (%d, 'police', %d)
                        """.formatted(personalId, personalId), "a GroupParty row on a PERSONAL base row");
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    void aGroupCannotPointAtAPartyThatIsNotAnAccount() throws SQLException {
        try (Connection connection = DriverManager.getConnection(freshUrl, "root", ROOT_PASSWORD)) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                int personalId = fetchGeneratedId(statement, "INSERT INTO Party (kind) VALUES ('PERSONAL') RETURNING partyId");
                statement.executeUpdate("INSERT INTO PersonalParty (partyId, playerUuid) VALUES (%d, '3a1c88f0-0000-0000-0000-0000000000b1')"
                        .formatted(personalId));
                int groupId = fetchGeneratedId(statement, "INSERT INTO Party (kind) VALUES ('GROUP') RETURNING partyId");
                assertRejected(statement, """
                        INSERT INTO GroupParty (partyId, groupName, accountPartyId) VALUES (%d, 'police', %d)
                        """.formatted(groupId, personalId), "a group pointing at a personal party");
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    void aChildKindCannotBeAnythingButItsOwn() throws SQLException {
        try (Connection connection = DriverManager.getConnection(freshUrl, "root", ROOT_PASSWORD)) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                int accountId = fetchGeneratedId(statement, "INSERT INTO Party (kind) VALUES ('ACCOUNT') RETURNING partyId");
                assertRejected(statement, """
                        INSERT INTO PersonalParty (partyId, kind, playerUuid)
                        VALUES (%d, 'ACCOUNT', '3a1c88f0-0000-0000-0000-0000000000b2')
                        """.formatted(accountId), "a PersonalParty row whose kind says ACCOUNT");
            } finally {
                connection.rollback();
            }
        }
    }

    private static void assertRejected(Statement statement, String sql, String label) {
        Assertions.assertThrows(SQLException.class, () -> statement.executeUpdate(sql),
                "the database should reject " + label);
    }

    @Test
    void twoGroupsMayShareOneAccount() throws SQLException {
        try (Connection connection = DriverManager.getConnection(freshUrl, "root", ROOT_PASSWORD)) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                int accountPartyId = fetchGeneratedId(statement, "INSERT INTO Party (kind) VALUES ('ACCOUNT') RETURNING partyId");
                statement.executeUpdate("INSERT INTO AccountParty (partyId, accountId, accountKind) VALUES (%d, 42, 'GOVERNMENT')"
                        .formatted(accountPartyId));
                for (String group : List.of("police", "rangers")) {
                    int groupId = fetchGeneratedId(statement, "INSERT INTO Party (kind) VALUES ('GROUP') RETURNING partyId");
                    statement.executeUpdate("INSERT INTO GroupParty (partyId, groupName, accountPartyId) VALUES (%d, '%s', %d)"
                            .formatted(groupId, group, accountPartyId));
                }
                Assertions.assertEquals(2, count(statement,
                        "SELECT COUNT(*) FROM GroupParty WHERE accountPartyId = " + accountPartyId));
            } finally {
                connection.rollback();
            }
        }
    }
}
