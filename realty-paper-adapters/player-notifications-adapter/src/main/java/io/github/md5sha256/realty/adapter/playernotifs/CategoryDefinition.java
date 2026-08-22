package io.github.md5sha256.realty.adapter.playernotifs;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/**
 * One category declared in the module's {@code categories.yml}.
 *
 * <p>A category is simultaneously two things, which is why its metadata lives in one place rather
 * than being split across sections: it is a PlayerNotifications {@code dataType} — the unit players
 * opt in and out of in {@code /notifications preferences} — and it is the display grouping those
 * dialogs label. {@link #label} and {@link #description} are what a player reads there;
 * {@link #title} is what appears on the delivered notification itself.</p>
 *
 * <p>Mirrors the shape of PlayerNotifications' own {@code categories.yml}
 * ({@code NotificationCategoryDefinition}) so an operator who has configured one recognises the
 * other. The one addition is {@link #keys}: PN groups data types, whereas this module maps Realty
 * message keys onto the data type they are enqueued under, so the leaves here are message keys.</p>
 *
 * @param key         the category key, used verbatim as the PN data type
 * @param label       player-facing name shown in the preference dialogs
 * @param description player-facing explanation shown in the preference dialogs
 * @param title       heading rendered on the notification; falls back to {@code label} when blank
 * @param priority    delivery priority for every key in this category; higher sorts first
 * @param keys        the Realty message keys routed to this category; may be empty
 */
public record CategoryDefinition(@NotNull String key,
                                 @NotNull String label,
                                 @NotNull String description,
                                 @NotNull String title,
                                 int priority,
                                 @NotNull List<String> keys) {

    public CategoryDefinition {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(keys, "keys");
        if (key.isBlank()) {
            throw new IllegalArgumentException("A category key may not be blank");
        }
        keys = List.copyOf(keys);
    }

    /**
     * The heading to render: the configured title, or the label when no title was configured.
     *
     * <p>Defaulting to the label rather than to a generic constant means an operator who adds a
     * category and gives it only a label still gets that label on the notification, instead of a
     * bare {@code "Realty"} that tells the player nothing about which category it came from.</p>
     */
    public @NotNull String effectiveTitle() {
        return this.title.isBlank() ? this.label : this.title;
    }
}
