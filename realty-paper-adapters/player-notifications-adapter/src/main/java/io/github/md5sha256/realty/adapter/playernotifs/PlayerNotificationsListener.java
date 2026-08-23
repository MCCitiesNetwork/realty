package io.github.md5sha256.realty.adapter.playernotifs;

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
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Turns each Realty notification into exactly one PlayerNotifications notification, routed to the
 * data type its message key's {@link RealtyCategory} maps to.
 *
 * <p>Unlike the chat adapter, nothing here checks whether a target is online: handing the
 * notification to PN is the whole job, and PN decides per recipient which sinks it reaches and
 * when — including delivering to a player who is offline right now.</p>
 *
 * <p>{@link RealtyNotificationEvent} is fired synchronously, so this handler already runs on the
 * main thread.</p>
 */
public final class PlayerNotificationsListener implements Listener {

    /**
     * Every Realty notification is enqueued at the same priority. Ordering the inbox by category was
     * dropped along with the module's own category config: PlayerNotifications is where a server
     * decides how a category is presented, and a per-category priority set here would fight that.
     */
    private static final int PRIORITY = 0;

    private final NotificationEnqueuer enqueuer;

    /**
     * Held in an {@link AtomicReference} so {@code /realty reload} can swap it while this listener
     * stays registered; a handler racing the swap reads either the old or the new duration.
     */
    private final AtomicReference<Duration> expiry;
    private final Logger logger;

    /**
     * @param enqueuer hands the built notification to PlayerNotifications
     * @param expiry   how long an enqueued notification survives before PN expires it
     * @param logger   used only for the FINE unclaimed-key trace
     */
    public PlayerNotificationsListener(@NotNull NotificationEnqueuer enqueuer,
                                       @NotNull Duration expiry,
                                       @NotNull Logger logger) {
        this.enqueuer = Objects.requireNonNull(enqueuer, "enqueuer");
        this.expiry = new AtomicReference<>(Objects.requireNonNull(expiry, "expiry"));
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Replaces the expiry applied to every subsequent notification. Notifications already enqueued
     * keep the expiry they were given — PlayerNotifications stored it as an absolute instant.
     *
     * @param expiry the freshly read {@code expiry-days}
     */
    public void setExpiry(@NotNull Duration expiry) {
        this.expiry.set(Objects.requireNonNull(expiry, "expiry"));
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onNotification(@NotNull RealtyNotificationEvent event) {
        String messageKey = event.getMessageKey();
        String dataType = RealtyCategory.forMessageKey(messageKey).dataType();
        if (!RealtyCategory.isClaimed(messageKey)) {
            // Never dropped: an unknown key is far more likely to be a Realty key newer than this
            // module's category table than a mistake, and a player still wants to be told.
            this.logger.log(Level.FINE,
                    "Unclaimed Realty message key {0}; routing to {1}",
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
                Instant.now().plus(this.expiry.get()),
                new NotificationTarget(event.getTargets()),
                dataType,
                payload,
                PRIORITY);

        // overwriteAllowed is false: every Realty notification is a distinct event — a second
        // outbid is a second thing that happened, not a correction of the first — so none of them
        // may replace an earlier one in the player's inbox.
        this.enqueuer.enqueue(notification, false);
    }
}
