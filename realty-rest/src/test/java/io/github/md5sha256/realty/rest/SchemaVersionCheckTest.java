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
    void acceptsAnOlderDatabase() {
        Assertions.assertDoesNotThrow(
                () -> SchemaVersionCheck.verify(SchemaVersionCheck.EXPECTED_VERSION - 1));
    }

    @Test
    void refusesANewerDatabaseNamingBothVersions() {
        int newer = SchemaVersionCheck.EXPECTED_VERSION + 1;
        IllegalStateException thrown = Assertions.assertThrows(IllegalStateException.class,
                () -> SchemaVersionCheck.verify(newer));
        Assertions.assertTrue(thrown.getMessage().contains(String.valueOf(newer)));
        Assertions.assertTrue(thrown.getMessage()
                .contains(String.valueOf(SchemaVersionCheck.EXPECTED_VERSION)));
    }
}
