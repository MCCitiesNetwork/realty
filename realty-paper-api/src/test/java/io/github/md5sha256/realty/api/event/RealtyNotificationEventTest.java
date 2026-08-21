package io.github.md5sha256.realty.api.event;

import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

class RealtyNotificationEventTest {

    private static final class TestEvent extends RealtyNotificationEvent {
        TestEvent(UUID targetId, Component message, String regionId, UUID worldId) {
            super(targetId, message, regionId, worldId);
        }

        TestEvent(List<UUID> targetIds, Component message, String regionId, UUID worldId) {
            super(targetIds, message, regionId, worldId);
        }
    }

    @Test
    void singleTargetConstructorWrapsTargetInSingletonList() {
        UUID target = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        TestEvent event = new TestEvent(target, Component.text("hello"), "plot_1", worldId);

        Assertions.assertEquals(List.of(target), event.targetIds());
        Assertions.assertEquals(Component.text("hello"), event.message());
        Assertions.assertEquals("plot_1", event.regionId());
        Assertions.assertEquals(worldId, event.worldId());
    }

    @Test
    void targetIdsAreImmutable() {
        TestEvent event = new TestEvent(UUID.randomUUID(), Component.text("hi"), "plot_1",
                UUID.randomUUID());

        Assertions.assertThrows(UnsupportedOperationException.class,
                () -> event.targetIds().add(UUID.randomUUID()));
    }

    @Test
    void eventIsAsynchronous() {
        TestEvent event = new TestEvent(UUID.randomUUID(), Component.text("hi"), "plot_1",
                UUID.randomUUID());

        Assertions.assertTrue(event.isAsynchronous());
    }

    @Test
    void allSubclassesShareOneHandlerList() {
        TestEvent event = new TestEvent(UUID.randomUUID(), Component.text("hi"), "plot_1",
                UUID.randomUUID());

        Assertions.assertSame(RealtyNotificationEvent.getHandlerList(), event.getHandlers());
    }
}
