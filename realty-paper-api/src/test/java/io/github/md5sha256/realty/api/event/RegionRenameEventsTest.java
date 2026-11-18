package io.github.md5sha256.realty.api.event;

import io.github.md5sha256.realty.api.WorldGuardRegion;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

class RegionRenameEventsTest {

    private static final WorldGuardRegion REGION = new WorldGuardRegion(null, null);
    private static final UUID ACTOR = UUID.randomUUID();

    @Test
    void theRenameEventCarriesTheNewNameAndCanBeCancelled() {
        RegionRenameEvent event = new RegionRenameEvent(REGION, ACTOR, "new-name");

        Assertions.assertSame(REGION, event.getRegion());
        Assertions.assertEquals(Optional.of(ACTOR), event.getActorId());
        Assertions.assertEquals("new-name", event.getNewName());
        Assertions.assertFalse(event.isCancelled());
        event.setCancelled(true);
        Assertions.assertTrue(event.isCancelled());
    }

    @Test
    void theRenamedEventCarriesTheOldName() {
        RegionRenamedEvent event = new RegionRenamedEvent(REGION, ACTOR, "old-name");

        Assertions.assertSame(REGION, event.getRegion());
        Assertions.assertEquals(Optional.of(ACTOR), event.getActorId());
        Assertions.assertEquals("old-name", event.getOldName());
    }

    @Test
    void aConsoleRenameHasNoActor() {
        Assertions.assertEquals(Optional.empty(), new RegionRenameEvent(REGION, null, "new-name").getActorId());
        Assertions.assertEquals(Optional.empty(), new RegionRenamedEvent(REGION, null, "old-name").getActorId());
    }
}
