package io.github.md5sha256.realty.adapter.pn;

import io.github.md5sha256.playernotifications.api.TypedNotification;
import org.jetbrains.annotations.NotNull;

/**
 * The single operation {@link PlayerNotificationsListener} needs from PlayerNotifications'
 * {@code NotificationService}.
 *
 * <p>Narrowing the ~20-method service down to this one call is what lets the listener be tested
 * with a three-line recording fake instead of a mock server.</p>
 */
@FunctionalInterface
public interface NotificationEnqueuer {

    void enqueue(@NotNull TypedNotification<RealtyNotificationPayload> notification,
                 boolean overwriteAllowed);
}
