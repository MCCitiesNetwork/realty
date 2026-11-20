package io.github.md5sha256.realty.adapter.playernotifs;

import io.github.md5sha256.playernotifications.api.NotificationTarget;
import io.github.md5sha256.playernotifications.api.TypedNotification;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

class DeliveringEnqueuerTest {

    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final Logger logger = Logger.getLogger("DeliveringEnqueuerTest");
    private final List<LogRecord> logged = new ArrayList<>();

    DeliveringEnqueuerTest() {
        this.logger.setUseParentHandlers(false);
        this.logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                logged.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
    }

    private static TypedNotification<RealtyNotificationPayload> notification() {
        return new TypedNotification<>("notif-1", Instant.EPOCH, Instant.EPOCH.plusSeconds(60),
                new NotificationTarget(List.of(TARGET)), "realty-general",
                RealtyNotificationPayload.of("notification.region-renamed", Component.text("renamed"), null, null),
                0);
    }

    @Test
    void handsTheNotificationToDeliveryWithItsOverwriteFlag() {
        List<Boolean> overwrites = new ArrayList<>();
        List<TypedNotification<RealtyNotificationPayload>> delivered = new ArrayList<>();
        NotificationEnqueuer enqueuer = new DeliveringEnqueuer((notification, overwriteAllowed) -> {
            delivered.add(notification);
            overwrites.add(overwriteAllowed);
            return CompletableFuture.completedFuture(null);
        }, this.logger);

        TypedNotification<RealtyNotificationPayload> notification = notification();
        enqueuer.enqueue(notification, false);

        Assertions.assertEquals(List.of(notification), delivered);
        Assertions.assertEquals(List.of(false), overwrites);
        Assertions.assertTrue(this.logged.isEmpty());
    }

    @Test
    void returnsWithoutWaitingForDelivery() {
        CompletableFuture<Void> pending = new CompletableFuture<>();
        NotificationEnqueuer enqueuer = new DeliveringEnqueuer((notification, overwriteAllowed) -> pending,
                this.logger);

        // Would hang if the enqueuer joined on the delivery.
        enqueuer.enqueue(notification(), false);

        Assertions.assertFalse(pending.isDone());
    }

    @Test
    void logsAFailedDelivery() {
        CompletableFuture<Void> pending = new CompletableFuture<>();
        NotificationEnqueuer enqueuer = new DeliveringEnqueuer((notification, overwriteAllowed) -> pending,
                this.logger);
        enqueuer.enqueue(notification(), false);

        pending.completeExceptionally(new IllegalStateException("sink down"));

        Assertions.assertEquals(1, this.logged.size());
        LogRecord record = this.logged.getFirst();
        Assertions.assertEquals(Level.WARNING, record.getLevel());
        Assertions.assertTrue(record.getMessage().contains("notif-1"), record.getMessage());
        Assertions.assertInstanceOf(IllegalStateException.class, record.getThrown());
    }

    @Test
    void logsADeliveryThatThrowsBeforeReturningAFuture() {
        NotificationEnqueuer enqueuer = new DeliveringEnqueuer((notification, overwriteAllowed) -> {
            throw new IllegalStateException("store down");
        }, this.logger);

        enqueuer.enqueue(notification(), false);

        Assertions.assertEquals(1, this.logged.size());
        Assertions.assertEquals(Level.WARNING, this.logged.getFirst().getLevel());
    }
}
