package io.github.md5sha256.realty.api;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public interface NotificationService {

    void queueNotification(@NotNull UUID authorityId, @NotNull Component text);

    void queueNotification(@NotNull UUID authorityId, @NotNull Component text, long expiryEpochSecond);

    void queueNotification(@NotNull UUID authorityId, @NotNull String plainText);

    void queueNotification(@NotNull UUID authorityId, @NotNull String plaintext, long expiryEpochSecond);

}
