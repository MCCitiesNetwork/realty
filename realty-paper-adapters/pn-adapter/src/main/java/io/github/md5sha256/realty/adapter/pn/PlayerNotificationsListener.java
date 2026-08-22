package io.github.md5sha256.realty.adapter.pn;

import io.github.md5sha256.playernotifications.api.NotificationTarget;
import io.github.md5sha256.playernotifications.api.TypedNotification;
import io.github.md5sha256.realty.api.WorldGuardRegion;
import io.github.md5sha256.realty.api.event.RealtyNotificationEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Turns each Realty notification into exactly one PlayerNotifications notification, routed to the
 * data type its message key maps to.
 *
 * <p>Unlike the chat adapter, nothing here checks whether a target is online: handing the
 * notification to PN is the whole job, and PN decides per recipient which sinks it reaches and
 * when — including delivering to a player who is offline right now.</p>
 *
 * <p>{@link RealtyNotificationEvent} is fired synchronously, so this handler already runs on the
 * main thread.</p>
 */
public final class PlayerNotificationsListener implements Listener {

    private final NotificationEnqueuer enqueuer;
    private final NotificationCategoryMapper categoryMapper;
    private final Duration expiry;
    private final Logger logger;

    /**
     * @param enqueuer       hands the built notification to PlayerNotifications
     * @param categoryMapper resolves data type and priority from the event's message key
     * @param expiry         how long an enqueued notification survives before PN expires it
     * @param logger         used only for the FINE unmapped-key trace
     */
    public PlayerNotificationsListener(@NotNull NotificationEnqueuer enqueuer,
                                       @NotNull NotificationCategoryMapper categoryMapper,
                                       @NotNull Duration expiry,
                                       @NotNull Logger logger) {
        this.enqueuer = Objects.requireNonNull(enqueuer, "enqueuer");
        this.categoryMapper = Objects.requireNonNull(categoryMapper, "categoryMapper");
        this.expiry = Objects.requireNonNull(expiry, "expiry");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onNotification(@NotNull RealtyNotificationEvent event) {
        String messageKey = event.getMessageKey();
        String dataType = this.categoryMapper.dataTypeFor(messageKey);
        if (!this.categoryMapper.isMapped(messageKey)) {
            // Never dropped: an unknown key is far more likely to be a Realty key newer than this
            // module's categories.yml than a mistake, and a player still wants to be told.
            this.logger.log(Level.FINE,
                    "Unmapped Realty message key {0}; routing to {1}",
                    new Object[]{messageKey, dataType});
        }

        @Nullable WorldGuardRegion region = event.getRegion();
        @Nullable String regionId = region == null ? null : region.region().getId();
        @Nullable String worldId = region == null ? null : region.world().getUID().toString();

        RealtyNotificationPayload payload = RealtyNotificationPayload.of(
                messageKey, event.getMessage(), regionId, worldId);

        TypedNotification<RealtyNotificationPayload> notification = new TypedNotification<>(
                UUID.randomUUID().toString(),
                Instant.now(),
                Instant.now().plus(this.expiry),
                new NotificationTarget(event.getTargets()),
                dataType,
                payload,
                this.categoryMapper.priorityFor(messageKey));

        // overwriteAllowed is false: every Realty notification is a distinct event — a second
        // outbid is a second thing that happened, not a correction of the first — so none of them
        // may replace an earlier one in the player's inbox.
        this.enqueuer.enqueue(notification, false);
    }
}
