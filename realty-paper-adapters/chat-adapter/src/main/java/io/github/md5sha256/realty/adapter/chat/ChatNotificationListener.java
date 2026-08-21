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
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * Delivers Realty notifications to targets who are online, and drops them otherwise.
 *
 * <p>This is the baseline every server gets. Adapters that can reach offline players — the
 * Essentials mail adapter, for one — listen at a higher priority and handle that case.</p>
 */
public final class ChatNotificationListener implements Listener {

    private final Executor mainThreadExec;
    private final Function<UUID, Audience> playerLookup;

    public ChatNotificationListener(@NotNull Executor mainThreadExec,
                                     @NotNull Function<UUID, Audience> playerLookup) {
        this.mainThreadExec = mainThreadExec;
        this.playerLookup = playerLookup;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onNotification(@NotNull RealtyNotificationEvent event) {
        Component message = event.message();
        List<UUID> targets = event.targetIds();
        this.mainThreadExec.execute(() -> {
            for (UUID target : targets) {
                @Nullable Audience audience = this.playerLookup.apply(target);
                if (audience != null) {
                    audience.sendMessage(message);
                }
            }
        });
    }
}
