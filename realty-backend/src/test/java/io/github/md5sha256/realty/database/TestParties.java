package io.github.md5sha256.realty.database;

import io.github.md5sha256.realty.api.AccountKind;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Writes party rows the way the mapper does, for tests that need a party the backend does not
 * create for them: a mapped group before the group commands exist, or an account party on its own.
 */
final class TestParties {

    private TestParties() {
    }

    /** The account's party id, creating its base and kind rows if it has none. */
    static int insertAccount(Connection connection, int accountId, AccountKind accountKind) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            try (ResultSet rs = statement.executeQuery("SELECT partyId FROM AccountParty WHERE accountId = " + accountId)) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
            int partyId = insertBase(statement, "ACCOUNT");
            statement.executeUpdate("INSERT INTO AccountParty (partyId, accountId, accountKind) VALUES (%d, %d, '%s')"
                    .formatted(partyId, accountId, accountKind.name()));
            return partyId;
        }
    }

    /** The new group's party id; the group points at the account's party, created if absent. */
    static int insertGroup(Connection connection, String groupName, int accountId, AccountKind accountKind)
            throws SQLException {
        int accountPartyId = insertAccount(connection, accountId, accountKind);
        try (Statement statement = connection.createStatement()) {
            int partyId = insertBase(statement, "GROUP");
            statement.executeUpdate("INSERT INTO GroupParty (partyId, groupName, accountPartyId) VALUES (%d, '%s', %d)"
                    .formatted(partyId, groupName, accountPartyId));
            return partyId;
        }
    }

    private static int insertBase(Statement statement, String kind) throws SQLException {
        try (ResultSet rs = statement.executeQuery("INSERT INTO Party (kind) VALUES ('" + kind + "') RETURNING partyId")) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
