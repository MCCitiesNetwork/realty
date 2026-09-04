package io.github.md5sha256.realty.rest;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class SchemaVersionCheckTest {

    @Test
    void acceptsTheExactExpectedVersion() {
        Assertions.assertDoesNotThrow(
                () -> SchemaVersionCheck.verify(SchemaVersionCheck.EXPECTED_VERSION));
    }

    @Test
    void refusesANewerDatabaseNamingBothVersions() {
        int newer = SchemaVersionCheck.EXPECTED_VERSION + 1;
        IllegalStateException thrown = Assertions.assertThrows(IllegalStateException.class,
                () -> SchemaVersionCheck.verify(newer));
        Assertions.assertTrue(thrown.getMessage().contains(String.valueOf(newer)),
                "message must name the applied version, was: " + thrown.getMessage());
        Assertions.assertTrue(thrown.getMessage()
                        .contains(String.valueOf(SchemaVersionCheck.EXPECTED_VERSION)),
                "message must name the expected version, was: " + thrown.getMessage());
        Assertions.assertTrue(thrown.getMessage().contains("newer"),
                "message must say which direction is wrong, was: " + thrown.getMessage());
    }

    @Test
    void refusesAnOlderDatabaseNamingBothVersions() {
        int older = SchemaVersionCheck.EXPECTED_VERSION - 1;
        IllegalStateException thrown = Assertions.assertThrows(IllegalStateException.class,
                () -> SchemaVersionCheck.verify(older));
        Assertions.assertTrue(thrown.getMessage().contains(String.valueOf(older)),
                "message must name the applied version, was: " + thrown.getMessage());
        Assertions.assertTrue(thrown.getMessage()
                        .contains(String.valueOf(SchemaVersionCheck.EXPECTED_VERSION)),
                "message must name the expected version, was: " + thrown.getMessage());
        Assertions.assertTrue(thrown.getMessage().contains("older"),
                "message must say which direction is wrong, was: " + thrown.getMessage());
    }

    /**
     * An unmigrated database reports version 0, which is the case an operator hits when
     * they point the service at an empty database before deploying the plugin. It must
     * fail with the same explanation as any other stale schema, not a bare zero.
     */
    @Test
    void refusesAnEmptyDatabaseReportingVersionZero() {
        IllegalStateException thrown = Assertions.assertThrows(IllegalStateException.class,
                () -> SchemaVersionCheck.verify(0));
        Assertions.assertTrue(thrown.getMessage().contains("older"),
                "message must explain the schema is behind, was: " + thrown.getMessage());
    }

    /**
     * The two directions must not share a message: an operator upgrading the plugin and
     * an operator who forgot to upgrade it need opposite instructions.
     */
    @Test
    void theTwoDirectionsGiveOppositeInstructions() {
        String newer = Assertions.assertThrows(IllegalStateException.class,
                () -> SchemaVersionCheck.verify(SchemaVersionCheck.EXPECTED_VERSION + 1))
                .getMessage();
        String older = Assertions.assertThrows(IllegalStateException.class,
                () -> SchemaVersionCheck.verify(SchemaVersionCheck.EXPECTED_VERSION - 1))
                .getMessage();

        Assertions.assertTrue(newer.contains("Upgrade realty-rest"),
                "a newer database means the API is behind, was: " + newer);
        Assertions.assertTrue(older.contains("Upgrade the Realty plugin"),
                "an older database means the plugin is behind, was: " + older);
    }
}
