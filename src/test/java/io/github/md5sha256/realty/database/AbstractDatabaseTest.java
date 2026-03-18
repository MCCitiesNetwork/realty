package io.github.md5sha256.realty.database;

import io.github.md5sha256.realty.DatabaseSettings;
import io.github.md5sha256.realty.database.maria.MariaDatabase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

@Testcontainers
abstract class AbstractDatabaseTest {

    private static final String ROOT_PASSWORD = "rootpass";

    @Container
    protected static final MariaDBContainer<?> CONTAINER = new MariaDBContainer<>("mariadb:11.7")
            .withEnv("MARIADB_ROOT_PASSWORD", ROOT_PASSWORD);

    protected static Database database;
    protected static RealtyLogicImpl logic;

    @BeforeAll
    static void initDatabase() throws IOException, SQLException {
        // Run DDL as root to avoid privilege issues with ALTER/CHECK constraints
        String baseJdbcUrl = CONTAINER.getJdbcUrl();
        String rootJdbcUrl = baseJdbcUrl + (baseJdbcUrl.contains("?") ? "&" : "?") + "allowMultiQueries=true";
        try (InputStream ddlStream = AbstractDatabaseTest.class.getClassLoader().getResourceAsStream("sql/maria_ddl.sql")) {
            String ddl = new String(ddlStream.readAllBytes(), StandardCharsets.UTF_8);
            try (Connection conn = DriverManager.getConnection(rootJdbcUrl, "root", ROOT_PASSWORD);
                 Statement stmt = conn.createStatement()) {
                stmt.execute(ddl);
            }
        }
        String jdbcUrl = CONTAINER.getJdbcUrl();
        // MariaDatabase prepends "jdbc:" to settings.url(), so strip the jdbc: prefix
        String url = jdbcUrl.substring("jdbc:".length());
        DatabaseSettings settings = new DatabaseSettings(url, CONTAINER.getUsername(), CONTAINER.getPassword());
        database = new MariaDatabase(settings);
        logic = new RealtyLogicImpl(database);
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
                    TRUNCATE TABLE SaleContractBidPayment;
                    TRUNCATE TABLE SaleContractBid;
                    TRUNCATE TABLE SaleContractOfferPayment;
                    TRUNCATE TABLE SaleContractOffer;
                    TRUNCATE TABLE SaleContractAuction;
                    TRUNCATE TABLE LeaseContract;
                    TRUNCATE TABLE SaleContract;
                    TRUNCATE TABLE Contract;
                    TRUNCATE TABLE RealtyRegion;
                    SET FOREIGN_KEY_CHECKS = 1;
                    """);
        }
    }
}
