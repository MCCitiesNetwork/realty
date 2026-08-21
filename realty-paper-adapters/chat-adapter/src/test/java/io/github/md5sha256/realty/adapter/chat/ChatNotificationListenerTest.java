package io.github.md5sha256.realty.adapter.chat;

import io.github.md5sha256.realty.api.event.OfferRejectedEvent;
import io.github.md5sha256.realty.api.event.RealtyNotificationEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

class ChatNotificationListenerTest {

    private static final Executor IMMEDIATE = Runnable::run;

    @Test
    void sendsToOnlineTargets() {
        UUID online = UUID.randomUUID();
        List<Component> received = new ArrayList<>();
        Map<UUID, Audience> players = new HashMap<>();
        players.put(online, new RecordingAudience(received));

        ChatNotificationListener listener =
                new ChatNotificationListener(IMMEDIATE, players::get);
        RealtyNotificationEvent event = new OfferRejectedEvent(List.of(online),
                Component.text("rejected"), "plot_1", UUID.randomUUID());

        listener.onNotification(event);

        Assertions.assertEquals(List.of(Component.text("rejected")), received);
    }

    @Test
    void offlineTargetIsSkippedWithoutThrowing() {
        ChatNotificationListener listener =
                new ChatNotificationListener(IMMEDIATE, uuid -> null);
        RealtyNotificationEvent event = new OfferRejectedEvent(List.of(UUID.randomUUID()),
                Component.text("rejected"), "plot_1", UUID.randomUUID());

        Assertions.assertDoesNotThrow(() -> listener.onNotification(event));
    }

    @Test
    void multiTargetEventFansOutOncePerOnlineTarget() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID offline = UUID.randomUUID();
        List<Component> received = new ArrayList<>();
        Map<UUID, Audience> players = new HashMap<>();
        players.put(first, new RecordingAudience(received));
        players.put(second, new RecordingAudience(received));

        ChatNotificationListener listener =
                new ChatNotificationListener(IMMEDIATE, players::get);
        RealtyNotificationEvent event = new OfferRejectedEvent(List.of(first, second, offline),
                Component.text("rejected"), "plot_1", UUID.randomUUID());

        listener.onNotification(event);

        Assertions.assertEquals(2, received.size());
    }

    private static final class RecordingAudience implements Audience {

        private final List<Component> received;

        RecordingAudience(List<Component> received) {
            this.received = received;
        }

        @Override
        public void sendMessage(Component message) {
            this.received.add(message);
        }
    }
}
