package io.github.md5sha256.realty.command.util;

import com.minecraftcitiesnetwork.pluginInfrastructure.util.DateFormatter;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.localisation.MessageKeys;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * The {@code <deadline>} placeholder of a payment message, the same for auctions and offers: the
 * deadline in the configured date format, or {@code pay.deadline-unknown} when it is not known.
 */
public final class Deadlines {

    private Deadlines() {}

    public static @NotNull TagResolver placeholder(@NotNull MessageContainer messages,
                                                   @NotNull SimpleDateFormat dateFormat,
                                                   @Nullable LocalDateTime deadline) {
        return deadline != null
                ? Placeholder.unparsed("deadline", DateFormatter.format(dateFormat, deadline))
                : Placeholder.component("deadline", messages.messageFor(MessageKeys.PAY_DEADLINE_UNKNOWN));
    }

    public static @NotNull TagResolver placeholder(@NotNull MessageContainer messages,
                                                   @NotNull SimpleDateFormat dateFormat,
                                                   @Nullable Instant deadline) {
        return placeholder(messages, dateFormat,
                deadline != null ? LocalDateTime.ofInstant(deadline, ZoneId.systemDefault()) : null);
    }
}
