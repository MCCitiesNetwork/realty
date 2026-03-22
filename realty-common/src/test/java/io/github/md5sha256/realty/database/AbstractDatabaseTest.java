package io.github.md5sha256.realty.database;

import io.github.md5sha256.realty.DatabaseSettings;
import io.github.md5sha256.realty.database.maria.MariaDatabase;
import io.github.md5sha256.realty.database.maria.MariaSchemaMigrator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.mariadb.MariaDBContainer;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.logging.Logger;

abstract class AbstractDatabaseTest {

    private static final String ROOT_PASSWORD = "rootpass";

    protected static final MariaDBContainer CONTAINER = new MariaDBContainer("mariadb:11.7")
            .withEnv("MARIADB_ROOT_PASSWORD", ROOT_PASSWORD);

    static {
        CONTAINER.start();
    }

    protected static Database database;
    protected static RealtyLogicImpl logic;

    private static volatile boolean migrated;

    @BeforeAll
    static void initDatabase() throws IOException, SQLException {
        if (!migrated) {
            // Run migrations as root to avoid privilege issues with ALTER/CHECK constraints
            String baseJdbcUrl = CONTAINER.getJdbcUrl();
            MariaSchemaMigrator.migrate(baseJdbcUrl, "root", ROOT_PASSWORD,
                    Path.of("sql/migrations"), MariaSchemaMigrator.defaultMigrations(), Logger.getLogger("test"));
            migrated = true;
        }

        String jdbcUrl = CONTAINER.getJdbcUrl();
        // MariaDatabase prepends "jdbc:" to settings.url(), so strip the jdbc: prefix
        String url = jdbcUrl.substring("jdbc:".length());
        DatabaseSettings settings = new DatabaseSettings(url, CONTAINER.getUsername(), CONTAINER.getPassword());
        database = new MariaDatabase(settings, Logger.getLogger("test"));
        logic = new RealtyLogicImpl(database, UUID::toString);
    }

    private static String truncateUrl;

    @BeforeEach
    void truncateTables() throws SQLException {
        if (truncateUrl == null) {
            String baseJdbcUrl = CONTAINER.getJdbcUrl();
            truncateUrl = baseJdbcUrl + (baseJdbcUrl.contains("?") ? "&" : "?") + "allowMultiQueries=true";
        }
        try (Connection conn = DriverManager.getConnection(truncateUrl, "root", ROOT_PASSWORD);
             Statement stmt = conn.createStatement()) {
            stmt.execute("""
                    SET FOREIGN_KEY_CHECKS = 0;
                    TRUNCATE TABLE AgentHistory;
                    TRUNCATE TABLE FreeholdHistory;
                    TRUNCATE TABLE LeaseHistory;
                    TRUNCATE TABLE FreeholdContractAgentInvite;
                    TRUNCATE TABLE FreeholdContractBidPayment;
                    TRUNCATE TABLE FreeholdContractBid;
                    TRUNCATE TABLE FreeholdContractOfferPayment;
                    TRUNCATE TABLE FreeholdContractOffer;
                    TRUNCATE TABLE FreeholdContractSanctionedAuctioneers;
                    TRUNCATE TABLE FreeholdContractAuction;
                    TRUNCATE TABLE LeaseContract;
                    TRUNCATE TABLE FreeholdContract;
                    TRUNCATE TABLE Contract;
                    TRUNCATE TABLE RealtyRegion;
                    SET FOREIGN_KEY_CHECKS = 1;
                    """);
        }
    }
}
