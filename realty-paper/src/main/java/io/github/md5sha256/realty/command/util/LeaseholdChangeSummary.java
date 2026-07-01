package io.github.md5sha256.realty.command.util;

import io.github.md5sha256.realty.api.CurrencyFormatter;
import io.github.md5sha256.realty.api.DurationFormatter;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.localisation.MessageKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders a leasehold term-change summary — the non-null fields of a proposed or applied modification —
 * as a localized component using the {@code modify.change-*} message keys. Shared by the {@code /realty
 * modify} listings and the region history so both describe changes identically.
 */
public final class LeaseholdChangeSummary {

    private LeaseholdChangeSummary() {}

    public static @NotNull Component render(@NotNull MessageContainer messages,
                                            @Nullable Double price,
                                            @Nullable Long durationSeconds,
                                            @Nullable Integer maxExtensions) {
        List<Component> parts = new ArrayList<>();
        if (price != null) {
            parts.add(messages.messageFor(MessageKeys.MODIFY_CHANGE_PRICE,
                    Placeholder.unparsed("value", CurrencyFormatter.format(price))));
        }
        if (durationSeconds != null) {
            parts.add(messages.messageFor(MessageKeys.MODIFY_CHANGE_DURATION,
                    Placeholder.unparsed("value",
                            DurationFormatter.format(Duration.ofSeconds(durationSeconds)))));
        }
        if (maxExtensions != null) {
            parts.add(messages.messageFor(MessageKeys.MODIFY_CHANGE_MAX_EXTENSIONS,
                    Placeholder.unparsed("value", String.valueOf(maxExtensions))));
        }
        if (parts.isEmpty()) {
            return messages.messageFor(MessageKeys.MODIFY_CHANGES_NONE);
        }
        return Component.join(JoinConfiguration.separator(Component.text(", ")), parts);
    }
}
