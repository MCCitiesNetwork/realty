package io.github.md5sha256.realty.rest;

import io.github.md5sha256.realty.DatabaseSettings;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Refuses to start against a database migrated past what this build understands.
 *
 * <p>A service that silently serves columns it misreads is worse than one that does
 * not start: the failure here is immediate and names the real cause, which is a
 * plugin upgraded ahead of the API.</p>
 */
public final class SchemaVersionCheck {

    /**
     * The highest migration version this build was written against. Bump this
     * deliberately when a migration lands that the API must understand.
     */
    public static final int EXPECTED_VERSION = 16;

    private static final String SELECT_VERSION = """
            SELECT COALESCE(MAX(version), 0) FROM schema_version
            """;

    private SchemaVersionCheck() {
    }

    public static void verify(int appliedVersion) {
        if (appliedVersion > EXPECTED_VERSION) {
            throw new IllegalStateException(
                    "Database schema version " + appliedVersion + " is newer than this build "
                            + "understands (" + EXPECTED_VERSION + "). Upgrade realty-rest to match "
                            + "the Realty plugin before starting it.");
        }
    }

    public static int readAppliedVersion(@NotNull DatabaseSettings settings) throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:" + settings.url(), settings.username(), settings.password());
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(SELECT_VERSION)) {
            rs.next();
            return rs.getInt(1);
        }
    }

}
