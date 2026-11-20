package io.github.md5sha256.realty.adapter.playernotifs;

import io.github.md5sha256.playernotifications.api.TypedNotification;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Stores each notification and pushes it to the player's chosen media at once, through
 * PlayerNotifications' {@code NotificationService#enqueueAndDeliver}, so an online player hears
 * about it now rather than on their next join. Mute and silence still apply on PN's side.
 *
 * <p>The delivery runs on PN's own threads. This never waits for it: the listener runs on the main
 * thread, inside Realty's event dispatch. A delivery that fails is logged, since nothing else
 * reads its result. Its future completing means delivery was attempted, not that it arrived.</p>
 *
 * <p>Needs PlayerNotifications 1.5.0 or later: an older host has no {@code enqueueAndDeliver}, and
 * calling it there throws {@link AbstractMethodError}.</p>
 */
final class DeliveringEnqueuer implements NotificationEnqueuer {

    private final BiFunction<TypedNotification<RealtyNotificationPayload>, Boolean, CompletableFuture<Void>> deliver;
    private final Logger logger;

    /**
     * @param deliver {@code NotificationService::enqueueAndDeliver}
     * @param logger  where failed deliveries are reported
     */
    DeliveringEnqueuer(
            @NotNull BiFunction<TypedNotification<RealtyNotificationPayload>, Boolean, CompletableFuture<Void>> deliver,
            @NotNull Logger logger) {
        this.deliver = Objects.requireNonNull(deliver, "deliver");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public void enqueue(@NotNull TypedNotification<RealtyNotificationPayload> notification,
                        boolean overwriteAllowed) {
        CompletableFuture<Void> delivery;
        try {
            delivery = this.deliver.apply(notification, overwriteAllowed);
        } catch (RuntimeException ex) {
            logFailure(notification, ex);
            return;
        }
        delivery.whenComplete((ignored, ex) -> {
            if (ex != null) {
                logFailure(notification, ex);
            }
        });
    }

    private void logFailure(@NotNull TypedNotification<RealtyNotificationPayload> notification,
                            @NotNull Throwable ex) {
        this.logger.log(Level.WARNING, "PlayerNotifications could not deliver Realty notification "
                + notification.notifKey() + " (" + notification.notifPayload().messageKey() + ") to "
                + notification.notifTarget().playerUUIDs(), ex);
    }
}
