package io.github.md5sha256.realty.database.maria;

import io.github.md5sha256.realty.database.migration.MigrationStep;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

public final class MariaSchemaMigrator {

    private static final String DRIVER_CLASS = "org.mariadb.jdbc.Driver";

    private static final String BOOTSTRAP_DDL = """
            CREATE TABLE IF NOT EXISTS schema_version
            (
                version     INT          NOT NULL PRIMARY KEY,
                description VARCHAR(255) NOT NULL,
                applied_at  DATETIME     NOT NULL DEFAULT NOW()
            )
            """;

    private static final String SELECT_VERSION = """
            SELECT COALESCE(MAX(version), 0) FROM schema_version
            """;

    private static final String INSERT_VERSION = """
            INSERT INTO schema_version (version, description) VALUES (?, ?)
            """;

    private static final List<MigrationStep> DEFAULT_MIGRATIONS = List.of(
            new MigrationStep(1, "initial schema", "V1__maria_initial_schema.sql")
    );

    private MariaSchemaMigrator() {
    }

    public static @NotNull List<MigrationStep> defaultMigrations() {
        return DEFAULT_MIGRATIONS;
    }

    public static void migrate(
            @NotNull String jdbcUrl,
            @NotNull String username,
            @NotNull String password,
            @NotNull Path baseResourceDir,
            @NotNull List<MigrationStep> steps,
            @NotNull Logger logger
    ) throws IOException, SQLException {
        Properties props = new Properties();
        props.setProperty("user", username);
        props.setProperty("password", password);
        props.setProperty("allowMultiQueries", "true");
        PooledDataSource dataSource = new PooledDataSource(DRIVER_CLASS, jdbcUrl, props);
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.execute(BOOTSTRAP_DDL);
            }
            connection.commit();

            int currentVersion;
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery(SELECT_VERSION)) {
                rs.next();
                currentVersion = rs.getInt(1);
            }

            for (MigrationStep step : steps) {
                if (step.version() <= currentVersion) {
                    continue;
                }
                String resourcePath = baseResourceDir.resolve(step.resourcePath()).toString().replace('\\', '/');
                String sql = loadResource(resourcePath);
                try (Statement statement = connection.createStatement()) {
                    statement.execute(sql);
                }
                try (PreparedStatement ps = connection.prepareStatement(INSERT_VERSION)) {
                    ps.setInt(1, step.version());
                    ps.setString(2, step.description());
                    ps.executeUpdate();
                }
                connection.commit();
                logger.info("Applied migration V" + step.version() + ": " + step.description());
            }
        } finally {
            dataSource.forceCloseAll();
        }
    }

    private static @NotNull String loadResource(@NotNull String resourcePath) throws IOException {
        try (InputStream is = MariaSchemaMigrator.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Migration resource not found: " + resourcePath);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
