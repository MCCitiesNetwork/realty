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
 * Migration 21 changes existing payment rows, so it is tested as the upgrade it is: a database
 * at version 20 holding a bid payment and an offer payment, then migrated.
 */
class SaleFeesMigrationTest extends AbstractDatabaseTest {

    private static final String DATABASE = "sale_fees";
    private static final String ROOT_PASSWORD = "rootpass";

    private static final UUID WORLD_ID = UUID.fromString("8f4d1c2e-0000-0000-0000-0000000000c1");
    private static final UUID AUTHORITY = UUID.fromString("3a1c88f0-0000-0000-0000-0000000000c1");
    private static final UUID BUYER = UUID.fromString("3a1c88f0-0000-0000-0000-0000000000c2");

    @Test
    void existingPaymentsKeepTheOldRulesAndOweTheirPrice() throws Exception {
        String containerUrl = CONTAINER.getJdbcUrl();
        String freshUrl = containerUrl.substring(0, containerUrl.lastIndexOf('/') + 1) + DATABASE;
        try (Connection root = DriverManager.getConnection(containerUrl, "root", ROOT_PASSWORD);
             Statement statement = root.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + DATABASE);
            statement.execute("CREATE DATABASE " + DATABASE);
        }

        List<MigrationStep> all = MariaSchemaMigrator.defaultMigrations();
        List<MigrationStep> upToTwenty = all.stream().filter(step -> step.version() <= 20).toList();
        MariaSchemaMigrator.migrate(freshUrl, "root", ROOT_PASSWORD,
                Path.of("sql/migrations"), upToTwenty, Logger.getLogger("test"));

        int bidRegion;
        int offerRegion;
        try (Connection connection = DriverManager.getConnection(freshUrl, "root", ROOT_PASSWORD);
             Statement statement = connection.createStatement()) {
            int partyId = fetchInt(statement,
                    "INSERT INTO Party (kind) VALUES ('PERSONAL') RETURNING partyId");
            statement.executeUpdate("INSERT INTO PersonalParty (partyId, playerUuid) VALUES (%d, '%s')"
                    .formatted(partyId, AUTHORITY));

            bidRegion = insertFreehold(statement, partyId, "plot_bid");
            insertBidPayment(statement, bidRegion, 800.0, 200.0);

            offerRegion = insertFreehold(statement, partyId, "plot_offer");
            int offerId = insertOffer(statement, offerRegion, 500.0);
            statement.executeUpdate("""
                    INSERT INTO FreeholdContractOfferPayment (offerId, realtyRegionId, offererId, offerPrice, paymentDeadline, currentPayment)
                    VALUES (%d, %d, '%s', 500.0, NOW() + INTERVAL 1 DAY, 100.0)
                    """.formatted(offerId, offerRegion, BUYER));
        }

        MariaSchemaMigrator.migrate(freshUrl, "root", ROOT_PASSWORD,
                Path.of("sql/migrations"), all, Logger.getLogger("test"));

        try (Connection connection = DriverManager.getConnection(freshUrl, "root", ROOT_PASSWORD);
             Statement statement = connection.createStatement()) {
            try (ResultSet rs = statement.executeQuery(
                    "SELECT totalDue, oldRules FROM FreeholdContractBidPayment WHERE realtyRegionId = " + bidRegion)) {
                Assertions.assertTrue(rs.next());
                Assertions.assertEquals(800.0, rs.getDouble("totalDue"), 0.0001);
                Assertions.assertTrue(rs.getBoolean("oldRules"), "an existing bid payment keeps the old rules");
            }
            try (ResultSet rs = statement.executeQuery(
                    "SELECT totalDue, oldRules, acceptedBy FROM FreeholdContractOfferPayment WHERE realtyRegionId = " + offerRegion)) {
                Assertions.assertTrue(rs.next());
                Assertions.assertEquals(500.0, rs.getDouble("totalDue"), 0.0001);
                Assertions.assertTrue(rs.getBoolean("oldRules"), "an existing offer payment keeps the old rules");
                Assertions.assertNull(rs.getObject("acceptedBy"));
            }

            int partyId = fetchInt(statement, "SELECT authorityPartyId FROM FreeholdContract LIMIT 1");
            int newRegion = insertFreehold(statement, partyId, "plot_new_bid");
            insertBidPaymentWithTotal(statement, newRegion, 1000.0, 1050.0, 1050.0);
            Assertions.assertFalse(fetchBoolean(statement,
                            "SELECT oldRules FROM FreeholdContractBidPayment WHERE realtyRegionId = " + newRegion),
                    "a payment inserted after the migration follows the new rules");

            int overRegion = insertFreehold(statement, partyId, "plot_over_bid");
            Assertions.assertThrows(SQLException.class,
                    () -> insertBidPaymentWithTotal(statement, overRegion, 1000.0, 1050.0, 1060.0),
                    "currentPayment above totalDue breaks the check");

            int newOfferRegion = insertFreehold(statement, partyId, "plot_over_offer");
            int newOfferId = insertOffer(statement, newOfferRegion, 500.0);
            Assertions.assertThrows(SQLException.class, () -> statement.executeUpdate("""
                    INSERT INTO FreeholdContractOfferPayment (offerId, realtyRegionId, offererId, offerPrice, paymentDeadline, currentPayment, totalDue)
                    VALUES (%d, %d, '%s', 500.0, NOW() + INTERVAL 1 DAY, 530.0, 525.0)
                    """.formatted(newOfferId, newOfferRegion, BUYER)),
                    "an offer currentPayment above totalDue breaks the check");

            Assertions.assertEquals(21, fetchInt(statement, "SELECT MAX(version) FROM schema_version"));
            for (String table : List.of("RegionFee", "RegionLevy", "AuthorityLevy", "SaleFee", "PendingRefund")) {
                Assertions.assertEquals(0, fetchInt(statement, "SELECT COUNT(*) FROM " + table),
                        table + " should exist and be empty");
            }
        }
    }

    private static int insertFreehold(Statement statement, int authorityPartyId, String worldGuardRegionId)
            throws SQLException {
        int regionId = fetchInt(statement, """
                INSERT INTO RealtyRegion (worldGuardRegionId, worldId)
                VALUES ('%s', '%s')
                RETURNING realtyRegionId
                """.formatted(worldGuardRegionId, WORLD_ID));
        int freeholdContractId = fetchInt(statement, """
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

    private static int insertOffer(Statement statement, int realtyRegionId, double price) throws SQLException {
        return fetchInt(statement, """
                INSERT INTO FreeholdContractOffer (realtyRegionId, offererId, offerPrice)
                VALUES (%d, '%s', %s)
                RETURNING offerId
                """.formatted(realtyRegionId, BUYER, price));
    }

    private record Bid(int bidId, int auctionId) {
    }

    private static Bid insertBid(Statement statement, int realtyRegionId, double price) throws SQLException {
        int auctionId = fetchInt(statement, """
                INSERT INTO FreeholdContractAuction (realtyRegionId, auctioneerId, startDate, biddingDurationSeconds,
                                                     paymentDurationSeconds, minBid, minStep)
                VALUES (%d, '%s', NOW(), 3600, 3600, 1.0, 1.0)
                RETURNING freeholdContractAuctionId
                """.formatted(realtyRegionId, AUTHORITY));
        int bidId = fetchInt(statement, """
                INSERT INTO FreeholdContractBid (freeholdContractAuctionId, bidderId, bidPrice)
                VALUES (%d, '%s', %s)
                RETURNING bidId
                """.formatted(auctionId, BUYER, price));
        return new Bid(bidId, auctionId);
    }

    private static void insertBidPayment(Statement statement, int realtyRegionId, double price, double paid)
            throws SQLException {
        Bid bid = insertBid(statement, realtyRegionId, price);
        statement.executeUpdate("""
                INSERT INTO FreeholdContractBidPayment (bidId, freeholdContractAuctionId, realtyRegionId, bidderId,
                                                        bidPrice, paymentDeadline, currentPayment)
                VALUES (%d, %d, %d, '%s', %s, NOW() + INTERVAL 1 DAY, %s)
                """.formatted(bid.bidId(), bid.auctionId(), realtyRegionId, BUYER, price, paid));
    }

    private static void insertBidPaymentWithTotal(Statement statement, int realtyRegionId, double price,
                                                  double totalDue, double paid) throws SQLException {
        Bid bid = insertBid(statement, realtyRegionId, price);
        statement.executeUpdate("""
                INSERT INTO FreeholdContractBidPayment (bidId, freeholdContractAuctionId, realtyRegionId, bidderId,
                                                        bidPrice, paymentDeadline, currentPayment, totalDue)
                VALUES (%d, %d, %d, '%s', %s, NOW() + INTERVAL 1 DAY, %s, %s)
                """.formatted(bid.bidId(), bid.auctionId(), realtyRegionId, BUYER, price, paid, totalDue));
    }

    private static int fetchInt(Statement statement, String sql) throws SQLException {
        try (ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static boolean fetchBoolean(Statement statement, String sql) throws SQLException {
        try (ResultSet rs = statement.executeQuery(sql)) {
            Assertions.assertTrue(rs.next());
            return rs.getBoolean(1);
        }
    }
}
