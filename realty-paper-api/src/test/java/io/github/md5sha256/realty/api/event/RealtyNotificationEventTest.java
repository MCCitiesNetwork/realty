package io.github.md5sha256.realty.api.event;

import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

class RealtyNotificationEventTest {

    private static final Component MESSAGE = Component.text("rendered");
    private static final String KEY = "notification.outbid";

    @Test
    void exposesTargetsAndMessage() {
        UUID target = UUID.randomUUID();
        RealtyNotificationEvent event =
                new RealtyNotificationEvent(List.of(target), KEY, MESSAGE, null);

        Assertions.assertEquals(List.of(target), event.getTargets());
        Assertions.assertEquals(KEY, event.getMessageKey());
        Assertions.assertEquals(MESSAGE, event.getMessage());
        Assertions.assertNull(event.getRegion());
    }

    @Test
    void targetsAreDefensivelyCopiedAndImmutable() {
        List<UUID> mutable = new ArrayList<>();
        mutable.add(UUID.randomUUID());
        RealtyNotificationEvent event =
                new RealtyNotificationEvent(mutable, KEY, MESSAGE, null);

        mutable.add(UUID.randomUUID());

        Assertions.assertEquals(1, event.getTargets().size());
        Assertions.assertThrows(UnsupportedOperationException.class,
                () -> event.getTargets().add(UUID.randomUUID()));
    }

    @Test
    void rejectsEmptyTargets() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new RealtyNotificationEvent(List.of(), KEY, MESSAGE, null));
    }

    @Test
    void rejectsBlankMessageKey() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new RealtyNotificationEvent(List.of(UUID.randomUUID()), "", MESSAGE, null));
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new RealtyNotificationEvent(List.of(UUID.randomUUID()), "   ", MESSAGE, null));
    }

    @Test
    void rejectsNulls() {
        Assertions.assertThrows(NullPointerException.class,
                () -> new RealtyNotificationEvent(null, KEY, MESSAGE, null));
        Assertions.assertThrows(NullPointerException.class,
                () -> new RealtyNotificationEvent(List.of(UUID.randomUUID()), null, MESSAGE, null));
        Assertions.assertThrows(NullPointerException.class,
                () -> new RealtyNotificationEvent(List.of(UUID.randomUUID()), KEY, null, null));
    }

    @Test
    void isSynchronous() {
        RealtyNotificationEvent event =
                new RealtyNotificationEvent(List.of(UUID.randomUUID()), KEY, MESSAGE, null);

        Assertions.assertFalse(event.isAsynchronous());
    }

    @Test
    void handlerListIsShared() {
        RealtyNotificationEvent event =
                new RealtyNotificationEvent(List.of(UUID.randomUUID()), KEY, MESSAGE, null);

        Assertions.assertSame(RealtyNotificationEvent.getHandlerList(), event.getHandlers());
    }
}
