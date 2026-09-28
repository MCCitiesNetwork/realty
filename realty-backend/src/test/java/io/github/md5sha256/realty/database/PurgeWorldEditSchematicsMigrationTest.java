package io.github.md5sha256.realty.database;

import io.github.md5sha256.realty.database.maria.MariaSchemaMigrator;
import io.github.md5sha256.realty.database.migration.MigrationStep;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.logging.Logger;

/**
 * Migration 18 deletes data, so it is tested as the upgrade it is: a database at
 * version 17 holding a capture, then migrated.
 *
 * <p>The shared test database is already at the latest version by the time any test
 * runs, which would prove only that the statement parses. This one builds a database
 * of its own in the same container and stops it at 17 first.</p>
 */
class PurgeWorldEditSchematicsMigrationTest extends AbstractDatabaseTest {

    private static final String DATABASE = "purge_worldedit_schematics";
    private static final String ROOT_PASSWORD = "rootpass";

    @Test
    void capturesFromBeforeTheFormatChangedAreDeletedAndNothingElseIs() throws Exception {
        String containerUrl = CONTAINER.getJdbcUrl();
        String freshUrl = containerUrl.substring(0, containerUrl.lastIndexOf('/') + 1) + DATABASE;
        try (Connection root = DriverManager.getConnection(containerUrl, "root", ROOT_PASSWORD);
             Statement statement = root.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + DATABASE);
            statement.execute("CREATE DATABASE " + DATABASE);
        }

        List<MigrationStep> all = MariaSchemaMigrator.defaultMigrations();
        List<MigrationStep> upToSeventeen = all.stream().filter(step -> step.version() <= 17).toList();
        MariaSchemaMigrator.migrate(freshUrl, "root", ROOT_PASSWORD,
                Path.of("sql/migrations"), upToSeventeen, Logger.getLogger("test"));

        try (Connection connection = DriverManager.getConnection(freshUrl, "root", ROOT_PASSWORD)) {
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO RealtySchematic (realtyRegionId, data, capturedAt)
                    VALUES (?, ?, NOW())
                    """)) {
                insert.setInt(1, 1);
                // Gzip magic: how every WorldEdit schematic begins.
                insert.setBytes(2, new byte[]{0x1f, (byte) 0x8b, 8, 0, 0, 0});
                insert.executeUpdate();
            }
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        INSERT INTO RealtyWorld (worldId, worldName)
                        VALUES ('8f4d1c2e-0000-0000-0000-000000000099', 'world')
                        """);
            }
            Assertions.assertEquals(1, count(connection, "RealtySchematic"));
        }

        MariaSchemaMigrator.migrate(freshUrl, "root", ROOT_PASSWORD,
                Path.of("sql/migrations"), all, Logger.getLogger("test"));

        try (Connection connection = DriverManager.getConnection(freshUrl, "root", ROOT_PASSWORD)) {
            Assertions.assertEquals(0, count(connection, "RealtySchematic"),
                    "a WorldEdit capture survived the migration");
            Assertions.assertEquals(1, count(connection, "RealtyWorld"),
                    "the migration deleted from a table it should not touch");
        }
    }

    private static int count(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rows.next();
            return rows.getInt(1);
        }
    }
}
