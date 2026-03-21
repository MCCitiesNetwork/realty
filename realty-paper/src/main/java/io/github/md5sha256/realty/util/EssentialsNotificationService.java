package io.github.md5sha256.realty.util;

import com.earth2me.essentials.Console;
import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.IEssentials;
import io.github.md5sha256.realty.api.NotificationService;
import net.ess3.api.IUser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class EssentialsNotificationService implements NotificationService {

    private final IEssentials essentials;
    private final Executor mainThreadExec;
    private final Logger logger;

    public EssentialsNotificationService(@NotNull Executor executor) {
        essentials = (Essentials) Bukkit.getPluginManager().getPlugin("Essentials");
        this.mainThreadExec = executor;
        this.logger = Bukkit.getPluginManager().getPlugin("Realty").getLogger();
    }

    @Override
    public void queueNotification(@NotNull UUID authorityId,
                                  @NotNull Component text,
                                  long expiryEpochSecond) {
        queueNotification(authorityId,
                LegacyComponentSerializer.legacySection().serialize(text),
                expiryEpochSecond);
    }

    @Override
    public void queueNotification(@NotNull UUID authorityId,
                                  @NotNull String plaintext,
                                  long expiryEpochSecond) {
        Runnable runnable = () -> {
            IUser user = essentials.getUser(authorityId);
            if (user == null) {
                logger.warning("Failed to resolve Essentials user for UUID " + authorityId);
                return;
            }
            essentials.getMail()
                    .sendMail(user,
                            Console.getInstance(),
                            plaintext,
                            TimeUnit.SECONDS.toMillis(expiryEpochSecond));
        };
        if (Bukkit.isPrimaryThread()) {
            runnable.run();
        } else {
            this.mainThreadExec.execute(runnable);
        }
    }

    @Override
    public void queueNotification(@NotNull UUID authorityId, @NotNull Component text) {
        queueNotification(authorityId, LegacyComponentSerializer.legacySection().serialize(text));
    }

    @Override
    public void queueNotification(@NotNull UUID authorityId, @NotNull String plaintext) {
        Runnable runnable = () -> {
            IUser user = essentials.getUser(authorityId);
            if (user == null) {
                logger.warning("Failed to resolve Essentials user for UUID " + authorityId);
                return;
            }
            essentials.getMail().sendMail(user, Console.getInstance(), plaintext);
        };
        if (Bukkit.isPrimaryThread()) {
            runnable.run();
        } else {
            this.mainThreadExec.execute(runnable);
        }
    }
}
