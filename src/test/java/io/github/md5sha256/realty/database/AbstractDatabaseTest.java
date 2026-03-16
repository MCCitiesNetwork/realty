package io.github.md5sha256.realty.database;

import io.github.md5sha256.realty.DatabaseSettings;
import io.github.md5sha256.realty.database.maria.MariaDatabase;
import org.apache.ibatis.session.SqlSession;
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

    @BeforeEach
    void truncateTables() throws SQLException {
        try (SqlSessionWrapper wrapper = database.openSession(true);
             SqlSession session = wrapper.session();
             Connection conn = session.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("SET FOREIGN_KEY_CHECKS = 0");
            stmt.execute("TRUNCATE TABLE SaleContractBid");
            stmt.execute("TRUNCATE TABLE SaleContractOffer");
            stmt.execute("TRUNCATE TABLE SaleContractAuction");
            stmt.execute("TRUNCATE TABLE LeaseContract");
            stmt.execute("TRUNCATE TABLE SaleContract");
            stmt.execute("TRUNCATE TABLE Contract");
            stmt.execute("TRUNCATE TABLE RealtyRegion");
            stmt.execute("SET FOREIGN_KEY_CHECKS = 1");
        }
    }
}
