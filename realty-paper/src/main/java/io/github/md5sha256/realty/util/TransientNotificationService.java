package io.github.md5sha256.realty.util;

import io.github.md5sha256.realty.api.NotificationService;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class TransientNotificationService implements NotificationService {

    @Override
    public void queueNotification(@NotNull UUID authorityId, @NotNull Component text) {
        Player player = Bukkit.getPlayer(authorityId);
        if (player != null) {
            player.sendMessage(text);
        }
    }

    @Override
    public void queueNotification(@NotNull UUID authorityId,
                                  @NotNull Component text,
                                  long expiryEpochSecond) {
        queueNotification(authorityId, text);
    }

    @Override
    public void queueNotification(@NotNull UUID authorityId, @NotNull String plaintext) {
        Player player = Bukkit.getPlayer(authorityId);
        if (player != null) {
            player.sendPlainMessage(plaintext);
        }
    }

    @Override
    public void queueNotification(@NotNull UUID authorityId,
                                  @NotNull String plaintext,
                                  long expiryEpochSecond) {
        queueNotification(authorityId, plaintext);
    }
}
