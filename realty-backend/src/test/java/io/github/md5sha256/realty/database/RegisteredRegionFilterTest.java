package io.github.md5sha256.realty.database;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

/**
 * The intersection behind {@code /v1/regions/at}: WorldGuard answers with every region covering
 * a block, and only the ones Realty has registered may be reported.
 */
class RegisteredRegionFilterTest extends AbstractDatabaseTest {

    private static final UUID WORLD_ID = UUID.fromString("8f4d1c2e-0000-0000-0000-000000000071");
    private static final UUID OTHER_WORLD = UUID.fromString("8f4d1c2e-0000-0000-0000-000000000072");

    @BeforeEach
    void seed() {
        try (SqlSessionWrapper session = database.openSession(true)) {
            session.realtyRegionMapper().registerWorldGuardRegion("plot_a", WORLD_ID);
            session.realtyRegionMapper().registerWorldGuardRegion("plot_b", WORLD_ID);
            session.realtyRegionMapper().registerWorldGuardRegion("plot_a", OTHER_WORLD);
        }
    }

    private static List<String> registered(UUID worldId, List<String> candidates) {
        try (SqlSessionWrapper session = database.openSession(true)) {
            return session.realtyRegionMapper().selectRegisteredIds(worldId, candidates);
        }
    }

    @Test
    void keepsOnlyTheIdsRealtyHasRegistered() {
        Assertions.assertEquals(List.of("plot_a", "plot_b"),
                registered(WORLD_ID, List.of("plot_a", "spawn", "plot_b")),
                "an unregistered WorldGuard region must not reach the response");
    }

    @Test
    void scopesTheMatchToTheNamedWorld() {
        Assertions.assertEquals(List.of(),
                registered(OTHER_WORLD, List.of("plot_b")),
                "plot_b is registered, but in a different world");
        Assertions.assertEquals(List.of("plot_a"), registered(OTHER_WORLD, List.of("plot_a")));
    }

    @Test
    void anEmptyCandidateListMatchesNothingRatherThanEverything() {
        Assertions.assertEquals(List.of(), registered(WORLD_ID, List.of()));
    }

    @Test
    void reportsEachRegisteredIdOnce() {
        Assertions.assertEquals(List.of("plot_a"),
                registered(WORLD_ID, List.of("plot_a", "plot_a")));
    }
}
