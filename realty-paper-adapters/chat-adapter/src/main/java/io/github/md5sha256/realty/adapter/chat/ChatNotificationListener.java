package io.github.md5sha256.realty.adapter.chat;

import io.github.md5sha256.realty.api.event.RealtyNotificationEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Delivers Realty notifications to targets who are online, and drops them otherwise.
 *
 * <p>This is the baseline every server gets. Adapters that can reach offline players — the
 * Essentials mail adapter, for one — listen at a higher priority and handle that case.</p>
 *
 * <p><b>Exactly-once delivery per target.</b> This listener and the Essentials mail listener each
 * resolve a target's online-ness independently, but every target still receives exactly one of a
 * chat message or a mail, never both and never neither. That holds because {@link
 * RealtyNotificationEvent} is dispatched synchronously: both handlers run inside a single {@code
 * PluginManager.callEvent} on the main thread, this one at {@link EventPriority#NORMAL} and the
 * mail listener at {@link EventPriority#HIGH}, with no yield between them; online-ness only ever
 * changes on that same main thread (player join/quit), so it cannot change mid-dispatch. This
 * guarantee depends on the event staying synchronous — if it were ever made asynchronous, the two
 * online-ness checks would run independently and could race, reintroducing duplicate or missed
 * delivery.</p>
 *
 * <p>{@link RealtyNotificationEvent} is fired synchronously through {@code
 * RealtyEventDispatch.fireSync}, so this handler already runs on the main thread and does not need
 * to marshal there itself.</p>
 */
public final class ChatNotificationListener implements Listener {

    private final Function<UUID, Audience> playerLookup;

    public ChatNotificationListener(@NotNull Function<UUID, Audience> playerLookup) {
        this.playerLookup = playerLookup;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onNotification(@NotNull RealtyNotificationEvent event) {
        Component message = event.getMessage();
        List<UUID> targets = event.getTargets();
        for (UUID target : targets) {
            @Nullable Audience audience = this.playerLookup.apply(target);
            if (audience != null) {
                audience.sendMessage(message);
            }
        }
    }
}
