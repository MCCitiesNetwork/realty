package io.github.md5sha256.realty.api.event;

import io.github.md5sha256.realty.api.WorldGuardRegion;
import net.kyori.adventure.text.Component;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Fired whenever Realty has something to tell one or more players. The message is rendered by the
 * fire site from {@code messages.yml}; this event only carries it.
 *
 * <p>Realty itself delivers nothing — adapter modules listen for this event and decide what reaches
 * the target. It is fired alongside, not instead of, the domain event describing what happened.</p>
 *
 * <p>Synchronous: fired through {@code RealtyEventDispatch.fireSync}, so handlers run on the main
 * thread and may use the Bukkit API directly.</p>
 *
 * <p>Alongside the rendered message the event carries a {@linkplain #getMessageKey() message key}:
 * the {@code messages.yml} path the fire site rendered from, e.g. {@code "notification.outbid"}.
 * It is an identity for routing, filtering and categorisation only, and must never be rendered to
 * a player — the rendered text is {@link #getMessage()}. Consumers must tolerate unknown keys:
 * third-party fire sites may use keys of their own, and Realty may add new ones at any time, so a
 * key a consumer does not recognise has to fall back to sane default handling rather than being
 * dropped or treated as an error.</p>
 */
public final class RealtyNotificationEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final List<UUID> targets;
    private final String messageKey;
    private final Component message;
    private final WorldGuardRegion region;

    /**
     * @param targets    who should be told; never empty. Several targets means several people get
     *                   the <em>same</em> message — different text per person is separate events.
     * @param messageKey the {@code messages.yml} path the fire site rendered {@code message} from,
     *                   e.g. {@code "notification.outbid"}; never blank. This is an identity used
     *                   for routing, filtering and categorisation and must never be rendered to a
     *                   player. Consumers must tolerate keys they do not recognise.
     * @param message    the rendered message
     * @param region     the region this concerns, or null when it cannot be resolved — a refund is
     *                   still announced when its region has already been deleted
     */
    public RealtyNotificationEvent(@NotNull List<UUID> targets,
                                   @NotNull String messageKey,
                                   @NotNull Component message,
                                   @Nullable WorldGuardRegion region) {
        this.targets = List.copyOf(Objects.requireNonNull(targets, "targets"));
        if (this.targets.isEmpty()) {
            throw new IllegalArgumentException("A notification needs at least one target");
        }
        this.messageKey = Objects.requireNonNull(messageKey, "messageKey");
        if (this.messageKey.isBlank()) {
            throw new IllegalArgumentException("A notification needs a non-blank message key");
        }
        this.message = Objects.requireNonNull(message, "message");
        this.region = region;
    }

    public @NotNull List<UUID> getTargets() {
        return this.targets;
    }

    /**
     * The {@code messages.yml} path the fire site rendered {@link #getMessage()} from, e.g.
     * {@code "notification.outbid"}.
     *
     * <p>An identity for routing, filtering and categorisation — never render it to a player.
     * Consumers must tolerate unknown keys and fall back to default handling rather than dropping
     * the notification.</p>
     */
    public @NotNull String getMessageKey() {
        return this.messageKey;
    }

    public @NotNull Component getMessage() {
        return this.message;
    }

    /**
     * The region this notification concerns, or {@code null} when it cannot be resolved.
     *
     * <p>A null region is routine, not pathological: payment-expiry sweeps announce a refund
     * after the region itself has already been deleted, so no {@link WorldGuardRegion} exists to
     * report at that point.</p>
     */
    public @Nullable WorldGuardRegion getRegion() {
        return this.region;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
