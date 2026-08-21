package io.github.md5sha256.realty.adapter.essentials;

import io.github.md5sha256.realty.api.event.RealtyNotificationEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Sends Realty notifications to offline targets as Essentials mail. Online targets are left to
 * the chat adapter, so every target receives exactly one of a chat message or a mail.
 *
 * <p><b>Exactly-once delivery per target.</b> This listener and the chat adapter's listener each
 * resolve a target's online-ness independently, but never disagree in a way that causes a
 * duplicate or a miss. That holds because {@link RealtyNotificationEvent} is dispatched
 * synchronously: both handlers run inside a single {@code PluginManager.callEvent} on the main
 * thread, the chat listener at {@link EventPriority#NORMAL} and this one at {@link
 * EventPriority#HIGH}, with no yield between them; online-ness only ever changes on that same
 * main thread (player join/quit), so it cannot change mid-dispatch. This guarantee depends on the
 * event staying synchronous — if it were ever made asynchronous, the two online-ness checks would
 * run independently and could race, reintroducing duplicate or missed delivery.</p>
 *
 * <p>Mail is a legacy-section format, so the Component is flattened on the way out via
 * {@link LegacyComponentSerializer#legacySection()} — RGB and hover/click data do not survive.</p>
 *
 * <p>{@link RealtyNotificationEvent} is fired synchronously through {@code
 * RealtyEventDispatch.fireSync}, so this handler already runs on the main thread and does not need
 * to marshal there itself.</p>
 */
public final class EssentialsMailListener implements Listener {

    private final BiConsumer<UUID, String> mailSender;
    private final Predicate<UUID> isOnline;
    private final Logger logger;

    /** Package-private: exists only so tests can omit the logger. */
    EssentialsMailListener(@NotNull BiConsumer<UUID, String> mailSender,
                           @NotNull Predicate<UUID> isOnline) {
        this(mailSender, isOnline, Logger.getLogger(EssentialsMailListener.class.getName()));
    }

    public EssentialsMailListener(@NotNull BiConsumer<UUID, String> mailSender,
                                  @NotNull Predicate<UUID> isOnline,
                                  @NotNull Logger logger) {
        this.mailSender = mailSender;
        this.isOnline = isOnline;
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onNotification(@NotNull RealtyNotificationEvent event) {
        String legacy = LegacyComponentSerializer.legacySection().serialize(event.getMessage());
        List<UUID> targets = event.getTargets();
        for (UUID target : targets) {
            if (this.isOnline.test(target)) {
                continue;
            }
            try {
                this.mailSender.accept(target, legacy);
            } catch (RuntimeException ex) {
                // One unresolvable user must not cost the other targets their mail.
                this.logger.log(Level.WARNING,
                        "Realty: failed to mail notification to " + target, ex);
            }
        }
    }
}
