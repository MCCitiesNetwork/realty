package io.github.md5sha256.realty.database;

import io.github.md5sha256.realty.database.entity.RealtySchematicEntity;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

class RealtySchematicMapperTest extends AbstractDatabaseTest {

    private static final UUID WORLD_ID = UUID.fromString("8f4d1c2e-0000-0000-0000-0000000000aa");
    private static final UUID CAPTURED_BY = UUID.fromString("8f4d1c2e-0000-0000-0000-0000000000bb");
    private static final LocalDateTime CAPTURED_AT = LocalDateTime.of(2026, 9, 4, 12, 30, 0);

    @Test
    void upsertThenSelectReturnsTheStoredBytes() {
        try (SqlSessionWrapper session = database.openSession(true)) {
            session.realtyRegionMapper().registerWorldGuardRegion("plot_a", WORLD_ID);

            int rows = session.realtySchematicMapper()
                    .upsert("plot_a", WORLD_ID, new byte[]{1, 2, 3}, CAPTURED_AT, CAPTURED_BY);
            Assertions.assertEquals(1, rows);

            RealtySchematicEntity found =
                    session.realtySchematicMapper().selectByWorldGuardRegion("plot_a", WORLD_ID);
            Assertions.assertNotNull(found);
            Assertions.assertArrayEquals(new byte[]{1, 2, 3}, found.data());
            Assertions.assertEquals(CAPTURED_AT, found.capturedAt());
            Assertions.assertEquals(CAPTURED_BY, found.capturedBy());
        }
    }

    @Test
    void upsertTwiceReplacesTheSchematicRatherThanInserting() {
        try (SqlSessionWrapper session = database.openSession(true)) {
            session.realtyRegionMapper().registerWorldGuardRegion("plot_a", WORLD_ID);
            session.realtySchematicMapper()
                    .upsert("plot_a", WORLD_ID, new byte[]{1}, CAPTURED_AT, CAPTURED_BY);
            session.realtySchematicMapper()
                    .upsert("plot_a", WORLD_ID, new byte[]{9, 9}, CAPTURED_AT.plusHours(1), CAPTURED_BY);

            RealtySchematicEntity found =
                    session.realtySchematicMapper().selectByWorldGuardRegion("plot_a", WORLD_ID);
            Assertions.assertNotNull(found);
            Assertions.assertArrayEquals(new byte[]{9, 9}, found.data());
            Assertions.assertEquals(CAPTURED_AT.plusHours(1), found.capturedAt());
        }
    }

    @Test
    void selectReturnsNullWhenTheRegionHasNoSchematic() {
        try (SqlSessionWrapper session = database.openSession(true)) {
            session.realtyRegionMapper().registerWorldGuardRegion("plot_a", WORLD_ID);
            Assertions.assertNull(
                    session.realtySchematicMapper().selectByWorldGuardRegion("plot_a", WORLD_ID));
        }
    }

    @Test
    void upsertAffectsNoRowsForAnUnregisteredRegion() {
        try (SqlSessionWrapper session = database.openSession(true)) {
            int rows = session.realtySchematicMapper()
                    .upsert("never_registered", WORLD_ID, new byte[]{1}, CAPTURED_AT, CAPTURED_BY);
            Assertions.assertEquals(0, rows);
        }
    }

    @Test
    void aRegionInAnotherWorldDoesNotShareASchematic() {
        UUID otherWorld = UUID.fromString("8f4d1c2e-0000-0000-0000-0000000000cc");
        try (SqlSessionWrapper session = database.openSession(true)) {
            session.realtyRegionMapper().registerWorldGuardRegion("plot_a", WORLD_ID);
            session.realtyRegionMapper().registerWorldGuardRegion("plot_a", otherWorld);
            session.realtySchematicMapper()
                    .upsert("plot_a", WORLD_ID, new byte[]{7}, CAPTURED_AT, CAPTURED_BY);

            Assertions.assertNull(
                    session.realtySchematicMapper().selectByWorldGuardRegion("plot_a", otherWorld));
        }
    }

    @Test
    void aLargeSchematicSurvivesTheRoundTrip() {
        // The column is LONGBLOB and captures are megabytes, not bytes; a driver or
        // column type that silently truncated would pass every test above.
        byte[] large = new byte[2 * 1024 * 1024];
        for (int i = 0; i < large.length; i++) {
            large[i] = (byte) (i % 251);
        }
        try (SqlSessionWrapper session = database.openSession(true)) {
            session.realtyRegionMapper().registerWorldGuardRegion("plot_a", WORLD_ID);
            session.realtySchematicMapper()
                    .upsert("plot_a", WORLD_ID, large, CAPTURED_AT, CAPTURED_BY);

            RealtySchematicEntity found =
                    session.realtySchematicMapper().selectByWorldGuardRegion("plot_a", WORLD_ID);
            Assertions.assertNotNull(found);
            Assertions.assertArrayEquals(large, found.data());
        }
    }
}
