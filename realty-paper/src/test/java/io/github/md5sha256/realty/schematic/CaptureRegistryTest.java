package io.github.md5sha256.realty.schematic;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.UUID;

class CaptureRegistryTest {

    private static final UUID WORLD_ID = UUID.fromString("8f4d1c2e-0000-0000-0000-0000000000ff");

    @Test
    void aRegionIsInFlightBetweenBeginAndFinish() {
        CaptureRegistry registry = new CaptureRegistry();
        Assertions.assertFalse(registry.isInFlight("plot_a", WORLD_ID));

        registry.begin("plot_a", WORLD_ID, Mockito.mock(TickSlicedCopy.class));
        Assertions.assertTrue(registry.isInFlight("plot_a", WORLD_ID));
        Assertions.assertFalse(registry.isInFlight("plot_b", WORLD_ID));

        registry.finish("plot_a", WORLD_ID);
        Assertions.assertFalse(registry.isInFlight("plot_a", WORLD_ID));
    }
}
