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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Migration 24 gives each tag its region's world, so it is tested as the upgrade it is: a database
 * at version 23 whose tags name regions by name only, then migrated.
 */
class RegionTagWorldMigrationTest extends AbstractDatabaseTest {

    private static final String DATABASE = "region_tag_world";
    private static final String ROOT_PASSWORD = "rootpass";
    private static final UUID WORLD_A = UUID.fromString("8f4d1c2e-0000-0000-0000-0000000000a1");
    private static final UUID WORLD_B = UUID.fromString("8f4d1c2e-0000-0000-0000-0000000000b1");

    @Test
    void eachTagGetsTheWorldOfEveryRegionWithItsName() throws Exception {
        String containerUrl = CONTAINER.getJdbcUrl();
        String freshUrl = containerUrl.substring(0, containerUrl.lastIndexOf('/') + 1) + DATABASE;
        try (Connection root = DriverManager.getConnection(containerUrl, "root", ROOT_PASSWORD);
             Statement statement = root.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + DATABASE);
            statement.execute("CREATE DATABASE " + DATABASE);
        }
        List<MigrationStep> all = MariaSchemaMigrator.defaultMigrations();
        MariaSchemaMigrator.migrate(freshUrl, "root", ROOT_PASSWORD, Path.of("sql/migrations"),
                all.stream().filter(step -> step.version() <= 23).toList(), Logger.getLogger("test"));

        try (Connection connection = DriverManager.getConnection(freshUrl, "root", ROOT_PASSWORD);
             Statement statement = connection.createStatement()) {
            insertRegion(statement, "plot_one_world", WORLD_A);
            insertRegion(statement, "plot_two_worlds", WORLD_A);
            insertRegion(statement, "plot_two_worlds", WORLD_B);
            insertTag(statement, "commercial", "plot_one_world");
            insertTag(statement, "waterfront", "plot_one_world");
            insertTag(statement, "commercial", "plot_two_worlds");
            // No registered region has this name: a region delete left the tag behind.
            insertTag(statement, "commercial", "plot_deleted");
        }

        MariaSchemaMigrator.migrate(freshUrl, "root", ROOT_PASSWORD, Path.of("sql/migrations"),
                all.stream().filter(step -> step.version() <= 24).toList(), Logger.getLogger("test"));

        try (Connection connection = DriverManager.getConnection(freshUrl, "root", ROOT_PASSWORD);
             Statement statement = connection.createStatement()) {
            Assertions.assertEquals(List.of(
                    "commercial plot_one_world " + WORLD_A,
                    "commercial plot_two_worlds " + WORLD_A,
                    "commercial plot_two_worlds " + WORLD_B,
                    "waterfront plot_one_world " + WORLD_A), tags(statement));

            // The same tag on the same name is now allowed once per world, and only once.
            statement.executeUpdate("INSERT INTO RegionTag (tagId, worldGuardRegionId, worldId) VALUES"
                    + " ('waterfront', 'plot_one_world', '" + WORLD_B + "')");
            Assertions.assertThrows(SQLException.class, () -> statement.executeUpdate(
                    "INSERT INTO RegionTag (tagId, worldGuardRegionId, worldId) VALUES"
                            + " ('waterfront', 'plot_one_world', '" + WORLD_B + "')"));
        }
    }

    private static void insertRegion(Statement statement, String worldGuardRegionId, UUID worldId)
            throws SQLException {
        statement.executeUpdate("INSERT INTO RealtyRegion (worldGuardRegionId, worldId) VALUES ('%s', '%s')"
                .formatted(worldGuardRegionId, worldId));
    }

    private static void insertTag(Statement statement, String tagId, String worldGuardRegionId) throws SQLException {
        statement.executeUpdate("INSERT INTO RegionTag (tagId, worldGuardRegionId) VALUES ('%s', '%s')"
                .formatted(tagId, worldGuardRegionId));
    }

    private static List<String> tags(Statement statement) throws SQLException {
        List<String> rows = new ArrayList<>();
        try (ResultSet rs = statement.executeQuery(
                "SELECT tagId, worldGuardRegionId, worldId FROM RegionTag ORDER BY tagId, worldGuardRegionId, worldId")) {
            while (rs.next()) {
                rows.add(rs.getString(1) + " " + rs.getString(2) + " " + rs.getString(3));
            }
        }
        return rows;
    }
}
