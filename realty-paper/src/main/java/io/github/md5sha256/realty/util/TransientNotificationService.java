package io.github.md5sha256.realty.util;

import io.github.md5sha256.realty.api.NotificationService;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.Executor;

public class TransientNotificationService implements NotificationService {

    private final Executor mainThreadExec;

    public TransientNotificationService(@NotNull Executor mainThreadExec) {
        this.mainThreadExec = mainThreadExec;
    }

    @Override
    public void queueNotification(@NotNull UUID authorityId, @NotNull Component text) {
        Runnable runnable = () -> {
            Player player = Bukkit.getPlayer(authorityId);
            if (player != null) {
                player.sendMessage(text);
            }
        };
        if (Bukkit.isPrimaryThread()) {
            runnable.run();
        } else {
            mainThreadExec.execute(runnable);
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
        Runnable runnable = () -> {
            Player player = Bukkit.getPlayer(authorityId);
            if (player != null) {
                player.sendPlainMessage(plaintext);
            }
        };
        if (Bukkit.isPrimaryThread()) {
            runnable.run();
        } else {
            mainThreadExec.execute(runnable);
        }
    }

    @Override
    public void queueNotification(@NotNull UUID authorityId,
                                  @NotNull String plaintext,
                                  long expiryEpochSecond) {
        queueNotification(authorityId, plaintext);
    }
}
