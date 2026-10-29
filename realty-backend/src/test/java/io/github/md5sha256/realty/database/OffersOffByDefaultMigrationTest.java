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
 * Migration 20 changes existing rows, so it is tested as the upgrade it is: a database at
 * version 19 holding freeholds with and without offers, then migrated.
 */
class OffersOffByDefaultMigrationTest extends AbstractDatabaseTest {

    private static final String DATABASE = "offers_off_by_default";
    private static final String ROOT_PASSWORD = "rootpass";

    private static final UUID WORLD_ID = UUID.fromString("8f4d1c2e-0000-0000-0000-0000000000b1");
    private static final UUID AUTHORITY = UUID.fromString("3a1c88f0-0000-0000-0000-0000000000b1");
    private static final UUID OFFERER = UUID.fromString("3a1c88f0-0000-0000-0000-0000000000b2");

    @Test
    void freeholdsTurnOffUnlessAnOfferIsOnThem() throws Exception {
        String containerUrl = CONTAINER.getJdbcUrl();
        String freshUrl = containerUrl.substring(0, containerUrl.lastIndexOf('/') + 1) + DATABASE;
        try (Connection root = DriverManager.getConnection(containerUrl, "root", ROOT_PASSWORD);
             Statement statement = root.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + DATABASE);
            statement.execute("CREATE DATABASE " + DATABASE);
        }

        List<MigrationStep> all = MariaSchemaMigrator.defaultMigrations();
        List<MigrationStep> upToNineteen = all.stream().filter(step -> step.version() <= 19).toList();
        MariaSchemaMigrator.migrate(freshUrl, "root", ROOT_PASSWORD,
                Path.of("sql/migrations"), upToNineteen, Logger.getLogger("test"));

        try (Connection connection = DriverManager.getConnection(freshUrl, "root", ROOT_PASSWORD);
             Statement statement = connection.createStatement()) {
            int partyId = fetchGeneratedId(statement,
                    "INSERT INTO Party (kind) VALUES ('PERSONAL') RETURNING partyId");
            statement.executeUpdate("INSERT INTO PersonalParty (partyId, playerUuid) VALUES (%d, '%s')"
                    .formatted(partyId, AUTHORITY));

            insertFreehold(statement, partyId, "plot_quiet");
            int openOfferRegion = insertFreehold(statement, partyId, "plot_open_offer");
            int payingRegion = insertFreehold(statement, partyId, "plot_paying");

            insertOffer(statement, openOfferRegion);
            int payingOffer = insertOffer(statement, payingRegion);
            statement.executeUpdate("""
                    INSERT INTO FreeholdContractOfferPayment (offerId, realtyRegionId, offererId, offerPrice, paymentDeadline, currentPayment)
                    VALUES (%d, %d, '%s', 500.0, NOW() + INTERVAL 1 DAY, 100.0)
                    """.formatted(payingOffer, payingRegion, OFFERER));
        }

        MariaSchemaMigrator.migrate(freshUrl, "root", ROOT_PASSWORD,
                Path.of("sql/migrations"), all, Logger.getLogger("test"));

        try (Connection connection = DriverManager.getConnection(freshUrl, "root", ROOT_PASSWORD);
             Statement statement = connection.createStatement()) {
            Assertions.assertFalse(acceptingOffers(statement, "plot_quiet"),
                    "a freehold with no offer on it should stop accepting offers");
            Assertions.assertTrue(acceptingOffers(statement, "plot_open_offer"),
                    "a freehold with an open offer should keep accepting offers");
            Assertions.assertTrue(acceptingOffers(statement, "plot_paying"),
                    "a freehold being paid for through an offer should keep accepting offers");

            int partyId = fetchGeneratedId(statement,
                    "SELECT authorityPartyId FROM FreeholdContract LIMIT 1");
            insertFreehold(statement, partyId, "plot_new");
            Assertions.assertFalse(acceptingOffers(statement, "plot_new"),
                    "a freehold created after the migration should not accept offers");
        }
    }

    private static int insertFreehold(Statement statement, int authorityPartyId, String worldGuardRegionId)
            throws SQLException {
        int regionId = fetchGeneratedId(statement, """
                INSERT INTO RealtyRegion (worldGuardRegionId, worldId)
                VALUES ('%s', '%s')
                RETURNING realtyRegionId
                """.formatted(worldGuardRegionId, WORLD_ID));
        int freeholdContractId = fetchGeneratedId(statement, """
                INSERT INTO FreeholdContract (authorityPartyId)
                VALUES (%d)
                RETURNING freeholdContractId
                """.formatted(authorityPartyId));
        statement.executeUpdate("""
                INSERT INTO Contract (contractId, contractType, realtyRegionId)
                VALUES (%d, 'freehold', %d)
                """.formatted(freeholdContractId, regionId));
        return regionId;
    }

    private static int insertOffer(Statement statement, int realtyRegionId) throws SQLException {
        return fetchGeneratedId(statement, """
                INSERT INTO FreeholdContractOffer (realtyRegionId, offererId, offerPrice)
                VALUES (%d, '%s', 500.0)
                RETURNING offerId
                """.formatted(realtyRegionId, OFFERER));
    }

    private static boolean acceptingOffers(Statement statement, String worldGuardRegionId) throws SQLException {
        try (ResultSet rs = statement.executeQuery("""
                SELECT fc.acceptingOffers
                FROM FreeholdContract fc
                INNER JOIN Contract c ON c.contractId = fc.freeholdContractId AND c.contractType = 'freehold'
                INNER JOIN RealtyRegion rr ON rr.realtyRegionId = c.realtyRegionId
                WHERE rr.worldGuardRegionId = '%s'
                """.formatted(worldGuardRegionId))) {
            Assertions.assertTrue(rs.next(), worldGuardRegionId + " not found");
            return rs.getBoolean(1);
        }
    }

    private static int fetchGeneratedId(Statement statement, String sql) throws SQLException {
        try (ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
