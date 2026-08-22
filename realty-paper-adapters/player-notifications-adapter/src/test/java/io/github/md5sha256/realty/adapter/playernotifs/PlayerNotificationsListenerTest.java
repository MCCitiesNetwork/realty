package io.github.md5sha256.realty.adapter.playernotifs;

import io.github.md5sha256.playernotifications.api.TypedNotification;
import io.github.md5sha256.realty.api.event.RealtyNotificationEvent;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

class PlayerNotificationsListenerTest {

    private static final NotificationCategoryMapper MAPPER = new NotificationCategoryMapper(
            Map.of("notification.outbid", "realty.auction",
                    "notification.region-bought", "realty.general"),
            Map.of("realty.auction", "Realty — Auction"),
            Map.of(),
            Map.of("realty.auction", 3));

    private static PlayerNotificationsListener listener(
            List<TypedNotification<RealtyNotificationPayload>> enqueued,
            List<Boolean> overwriteFlags) {
        return new PlayerNotificationsListener(
                (notification, overwriteAllowed) -> {
                    enqueued.add(notification);
                    overwriteFlags.add(overwriteAllowed);
                },
                MAPPER,
                Duration.ofDays(30),
                Logger.getLogger(PlayerNotificationsListenerTest.class.getName()));
    }

    @Test
    void oneEventEnqueuesExactlyOneNotification() {
        List<TypedNotification<RealtyNotificationPayload>> enqueued = new ArrayList<>();
        List<Boolean> overwriteFlags = new ArrayList<>();
        UUID target = UUID.randomUUID();

        listener(enqueued, overwriteFlags).onNotification(new RealtyNotificationEvent(
                List.of(target), "notification.outbid", Component.text("outbid"), null));

        Assertions.assertEquals(1, enqueued.size());
        TypedNotification<RealtyNotificationPayload> notification = enqueued.get(0);
        Assertions.assertEquals("realty.auction", notification.notifPayloadType());
        Assertions.assertEquals(3, notification.notifPriority());
        Assertions.assertEquals("notification.outbid", notification.notifPayload().messageKey());
    }

    @Test
    void targetsAreCarriedVerbatimIncludingMultipleTargets() {
        List<TypedNotification<RealtyNotificationPayload>> enqueued = new ArrayList<>();
        List<Boolean> overwriteFlags = new ArrayList<>();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        listener(enqueued, overwriteFlags).onNotification(new RealtyNotificationEvent(
                List.of(first, second), "notification.outbid", Component.text("outbid"), null));

        Assertions.assertEquals(List.of(first, second),
                enqueued.get(0).notifTarget().playerUUIDs());
    }

    @Test
    void overwriteIsNeverAllowed() {
        List<TypedNotification<RealtyNotificationPayload>> enqueued = new ArrayList<>();
        List<Boolean> overwriteFlags = new ArrayList<>();

        listener(enqueued, overwriteFlags).onNotification(new RealtyNotificationEvent(
                List.of(UUID.randomUUID()), "notification.outbid", Component.text("outbid"), null));

        Assertions.assertEquals(List.of(Boolean.FALSE), overwriteFlags);
    }

    @Test
    void twoEventsFromTheSameKeyGetDifferentNotificationKeys() {
        List<TypedNotification<RealtyNotificationPayload>> enqueued = new ArrayList<>();
        List<Boolean> overwriteFlags = new ArrayList<>();
        PlayerNotificationsListener listener = listener(enqueued, overwriteFlags);
        UUID target = UUID.randomUUID();

        listener.onNotification(new RealtyNotificationEvent(
                List.of(target), "notification.outbid", Component.text("outbid"), null));
        listener.onNotification(new RealtyNotificationEvent(
                List.of(target), "notification.outbid", Component.text("outbid again"), null));

        Assertions.assertNotEquals(enqueued.get(0).notifKey(), enqueued.get(1).notifKey());
    }

    @Test
    void aNullRegionYieldsNullRegionAndWorldIds() {
        List<TypedNotification<RealtyNotificationPayload>> enqueued = new ArrayList<>();
        List<Boolean> overwriteFlags = new ArrayList<>();

        listener(enqueued, overwriteFlags).onNotification(new RealtyNotificationEvent(
                List.of(UUID.randomUUID()),
                "notification.bid-payment-expired",
                Component.text("refunded"),
                null));

        RealtyNotificationPayload payload = enqueued.get(0).notifPayload();
        Assertions.assertNull(payload.regionId());
        Assertions.assertNull(payload.worldId());
    }

    @Test
    void anUnmappedKeyStillEnqueuesUnderGeneral() {
        List<TypedNotification<RealtyNotificationPayload>> enqueued = new ArrayList<>();
        List<Boolean> overwriteFlags = new ArrayList<>();

        listener(enqueued, overwriteFlags).onNotification(new RealtyNotificationEvent(
                List.of(UUID.randomUUID()),
                "notification.some-future-key",
                Component.text("something happened"),
                null));

        Assertions.assertEquals(1, enqueued.size());
        Assertions.assertEquals("realty.general", enqueued.get(0).notifPayloadType());
    }

    @Test
    void theRenderedMessageSurvivesIntoThePayload() {
        List<TypedNotification<RealtyNotificationPayload>> enqueued = new ArrayList<>();
        List<Boolean> overwriteFlags = new ArrayList<>();
        Component message = Component.text("You were outbid on plot1");

        listener(enqueued, overwriteFlags).onNotification(new RealtyNotificationEvent(
                List.of(UUID.randomUUID()), "notification.outbid", message, null));

        Assertions.assertEquals(message.compact(),
                enqueued.get(0).notifPayload().bodyComponent().compact());
    }
}
