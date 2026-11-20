package io.github.md5sha256.realty.api.event;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import io.github.md5sha256.realty.api.WorldGuardRegion;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

class RegionRedefineEventsTest {

    private static final WorldGuardRegion REGION = new WorldGuardRegion(null, null);
    private static final UUID ACTOR = UUID.randomUUID();
    private static final ProtectedRegion SHAPE =
            new ProtectedCuboidRegion("plot", BlockVector3.at(0, 0, 0), BlockVector3.at(4, 4, 4));

    @Test
    void theRedefineEventCarriesTheNewRegionAndCanBeCancelled() {
        RegionRedefineEvent event = new RegionRedefineEvent(REGION, ACTOR, SHAPE);

        Assertions.assertSame(REGION, event.getRegion());
        Assertions.assertEquals(Optional.of(ACTOR), event.getActorId());
        Assertions.assertSame(SHAPE, event.getNewRegion());
        Assertions.assertFalse(event.isCancelled());
        event.setCancelled(true);
        Assertions.assertTrue(event.isCancelled());
    }

    @Test
    void theRedefinedEventCarriesTheOldRegion() {
        RegionRedefinedEvent event = new RegionRedefinedEvent(REGION, ACTOR, SHAPE);

        Assertions.assertSame(REGION, event.getRegion());
        Assertions.assertEquals(Optional.of(ACTOR), event.getActorId());
        Assertions.assertSame(SHAPE, event.getOldRegion());
    }

    @Test
    void aConsoleRedefineHasNoActor() {
        Assertions.assertEquals(Optional.empty(), new RegionRedefineEvent(REGION, null, SHAPE).getActorId());
        Assertions.assertEquals(Optional.empty(), new RegionRedefinedEvent(REGION, null, SHAPE).getActorId());
    }
}
