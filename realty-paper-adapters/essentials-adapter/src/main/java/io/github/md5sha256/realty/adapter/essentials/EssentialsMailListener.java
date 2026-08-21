package io.github.md5sha256.realty.adapter.essentials;

import io.github.md5sha256.realty.api.event.RealtyNotificationEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Sends Realty notifications to offline targets as Essentials mail. Online targets are left to
 * the chat adapter, so on a best-effort basis nobody gets the same notification twice.
 *
 * <p><b>Known race, accepted.</b> This listener and the chat adapter's listener each resolve a
 * target's online-ness independently, inside their own main-thread task. A player who logs in or
 * out between those two tasks can therefore receive both a chat message and a mail, or neither.
 * The de-duplication below is best-effort, not a guarantee.</p>
 *
 * <p>Mail is a legacy-section format, so the Component is flattened on the way out — RGB and
 * hover/click data do not survive.</p>
 */
public final class EssentialsMailListener implements Listener {

    private final Executor mainThreadExec;
    private final BiConsumer<UUID, String> mailSender;
    private final Predicate<UUID> isOnline;
    private final Logger logger;

    /** Package-private: exists only so tests can omit the logger. */
    EssentialsMailListener(@NotNull Executor mainThreadExec,
                           @NotNull BiConsumer<UUID, String> mailSender,
                           @NotNull Predicate<UUID> isOnline) {
        this(mainThreadExec, mailSender, isOnline, Logger.getLogger(EssentialsMailListener.class.getName()));
    }

    public EssentialsMailListener(@NotNull Executor mainThreadExec,
                                  @NotNull BiConsumer<UUID, String> mailSender,
                                  @NotNull Predicate<UUID> isOnline,
                                  @NotNull Logger logger) {
        this.mainThreadExec = mainThreadExec;
        this.mailSender = mailSender;
        this.isOnline = isOnline;
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onNotification(@NotNull RealtyNotificationEvent event) {
        String legacy = LegacyComponentSerializer.legacySection().serialize(event.message());
        List<UUID> targets = event.targetIds();
        this.mainThreadExec.execute(() -> {
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
        });
    }
}
