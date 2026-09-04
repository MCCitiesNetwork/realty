package io.github.md5sha256.realty.database;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.UUID;

class RealtySchematicBackendTest extends AbstractDatabaseTest {

    private static final UUID WORLD_ID = UUID.fromString("8f4d1c2e-0000-0000-0000-0000000000dd");

    @Test
    void storeThenGetReturnsTheBytes() {
        try (SqlSessionWrapper session = database.openSession(true)) {
            session.realtyRegionMapper().registerWorldGuardRegion("plot_b", WORLD_ID);
        }
        Assertions.assertTrue(logic.storeSchematic("plot_b", WORLD_ID, new byte[]{4, 5}));
        Assertions.assertArrayEquals(new byte[]{4, 5}, logic.getSchematic("plot_b", WORLD_ID));
    }

    @Test
    void getReturnsNullWhenNothingWasCaptured() {
        try (SqlSessionWrapper session = database.openSession(true)) {
            session.realtyRegionMapper().registerWorldGuardRegion("plot_b", WORLD_ID);
        }
        Assertions.assertNull(logic.getSchematic("plot_b", WORLD_ID));
    }

    @Test
    void storeReportsFailureForAnUnregisteredRegion() {
        Assertions.assertFalse(logic.storeSchematic("nope", WORLD_ID, new byte[]{1}));
    }

    @Test
    void aReCaptureReportsSuccessAndReplacesTheBytes() {
        // MariaDB reports two affected rows for an ON DUPLICATE KEY UPDATE that
        // updates, so a "== 1" check here would call a successful re-capture a
        // failure and skip recording the cooldown.
        try (SqlSessionWrapper session = database.openSession(true)) {
            session.realtyRegionMapper().registerWorldGuardRegion("plot_b", WORLD_ID);
        }
        Assertions.assertTrue(logic.storeSchematic("plot_b", WORLD_ID, new byte[]{1}));
        Assertions.assertTrue(logic.storeSchematic("plot_b", WORLD_ID, new byte[]{2, 2}));
        Assertions.assertArrayEquals(new byte[]{2, 2}, logic.getSchematic("plot_b", WORLD_ID));
    }
}
