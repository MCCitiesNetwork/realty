package io.github.md5sha256.realty.rest;

import io.github.md5sha256.realty.DatabaseSettings;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Refuses to start unless the database schema is exactly the version this build was
 * written against.
 *
 * <p>Both directions are rejected, for different reasons. A <strong>newer</strong>
 * database may have changed the meaning of a column this build reads, and serving
 * columns it misreads is worse than not starting. An <strong>older</strong> database
 * may be missing tables this build depends on outright -- {@code RealtyWorld} arrives
 * in V16, and without it world listing and every {@code ?world=} lookup fail at
 * request time with a 500 rather than at startup with an explanation.</p>
 *
 * <p>The cost of exactness is that a plugin migration irrelevant to the API still
 * forces {@link #EXPECTED_VERSION} to be bumped and the service rebuilt. That is
 * accepted deliberately: a version gate that guesses which migrations matter is a
 * gate that eventually guesses wrong, and the failure it would produce is a runtime
 * 500 with no indication of the real cause.</p>
 */
public final class SchemaVersionCheck {

    /**
     * The exact migration version this build requires. Bump this whenever a migration
     * lands in {@code MariaSchemaMigrator.DEFAULT_MIGRATIONS}, whether or not the API
     * reads what it adds -- the check is an equality, not a floor.
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
        if (appliedVersion < EXPECTED_VERSION) {
            throw new IllegalStateException(
                    "Database schema version " + appliedVersion + " is older than this build "
                            + "requires (" + EXPECTED_VERSION + "). Upgrade the Realty plugin and "
                            + "let it run its migrations before starting realty-rest.");
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
