package io.github.md5sha256.realty.adapter.playernotifs;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Resolves a Realty message key to the PlayerNotifications {@code dataType} it is enqueued under,
 * and to the label, description, title and priority that data type is registered and rendered with.
 *
 * <p>The category set is whatever {@code categories.yml} declares — this class holds no hardcoded
 * list. Everything that registers against PlayerNotifications reads {@link #dataTypes()}, so adding
 * a category to the file is enough to have it registered, claimed and shown in the preference
 * dialogs; nothing needs recompiling.</p>
 *
 * <p>Deliberately a plain class with no PlayerNotifications and no Bukkit types on it: the routing
 * decision is the part worth testing, and keeping it free of both means it can be tested without a
 * server or a live PN install.</p>
 *
 * <p>An unrecognised key resolves to {@link #fallbackDataType()} rather than throwing or dropping.
 * Realty adds message keys over time and third-party fire sites may use keys of their own; a
 * notification the mapper has never seen is still a notification a player should receive.</p>
 */
public final class NotificationCategoryMapper {

    private static final String DEFAULT_TITLE = "Realty";

    private final Map<String, CategoryDefinition> categories;
    private final Map<String, String> keyToDataType;
    private final Map<String, String> titleOverrides;
    private final String fallbackDataType;
    /** Declaration order, preserved so registration and unregistration are reproducible. */
    private final List<String> orderedKeys;

    /**
     * @param categories       the declared categories, in the order they should be registered
     * @param titleOverrides   message key to title, beating the title of the key's category
     * @param fallbackDataType the category unmapped keys route to; must be one of {@code categories}
     * @throws IllegalArgumentException if {@code categories} is empty, declares the same category
     *                                  key twice, claims one message key from two categories, or if
     *                                  {@code fallbackDataType} is not a declared category
     */
    public NotificationCategoryMapper(@NotNull List<CategoryDefinition> categories,
                                      @NotNull Map<String, String> titleOverrides,
                                      @NotNull String fallbackDataType) {
        Objects.requireNonNull(categories, "categories");
        Objects.requireNonNull(fallbackDataType, "fallbackDataType");
        if (categories.isEmpty()) {
            throw new IllegalArgumentException(
                    "categories.yml declares no categories; at least one is required so that "
                            + "notifications have somewhere to be enqueued");
        }

        Map<String, CategoryDefinition> byKey = new LinkedHashMap<>();
        Map<String, String> routing = new HashMap<>();
        for (CategoryDefinition category : categories) {
            CategoryDefinition previous = byKey.put(category.key(), category);
            if (previous != null) {
                throw new IllegalArgumentException(
                        "categories.yml declares the category '" + category.key() + "' twice");
            }
            for (String messageKey : category.keys()) {
                // Rejected rather than last-wins: which of the two categories a player must enable
                // to receive the key would otherwise depend on file order, and neither the operator
                // nor the player could tell from the dialogs which one had won.
                String claimedBy = routing.put(messageKey, category.key());
                if (claimedBy != null) {
                    throw new IllegalArgumentException(
                            "categories.yml routes the message key '" + messageKey + "' to both '"
                                    + claimedBy + "' and '" + category.key()
                                    + "'; a key may belong to exactly one category");
                }
            }
        }
        if (!byKey.containsKey(fallbackDataType)) {
            // Failing here beats routing to it at runtime: an undeclared fallback is never
            // registered, so every unmapped notification would be enqueued under a data type with
            // no serializer and no renderer, and would be lost silently.
            throw new IllegalArgumentException(
                    "categories.yml sets fallback-category to '" + fallbackDataType
                            + "', which is not one of the declared categories " + byKey.keySet());
        }

        this.orderedKeys = List.copyOf(byKey.keySet());
        this.categories = Map.copyOf(byKey);
        this.keyToDataType = Map.copyOf(routing);
        this.titleOverrides = Map.copyOf(Objects.requireNonNull(titleOverrides, "titleOverrides"));
        this.fallbackDataType = fallbackDataType;
    }

    /**
     * Every data type this adapter registers, in the order {@code categories.yml} declares them.
     */
    public @NotNull List<String> dataTypes() {
        return this.orderedKeys;
    }

    /** The data type unmapped message keys route to. */
    public @NotNull String fallbackDataType() {
        return this.fallbackDataType;
    }

    /**
     * The data type the given message key routes to, or {@link #fallbackDataType()} if the key is
     * not mapped.
     */
    public @NotNull String dataTypeFor(@NotNull String messageKey) {
        Objects.requireNonNull(messageKey, "messageKey");
        return this.keyToDataType.getOrDefault(messageKey, this.fallbackDataType);
    }

    /**
     * Whether the given message key is explicitly mapped. Callers use this to log the fallback,
     * because {@link #dataTypeFor} cannot distinguish an unmapped key from one deliberately mapped
     * to the fallback category.
     */
    public boolean isMapped(@NotNull String messageKey) {
        return this.keyToDataType.containsKey(Objects.requireNonNull(messageKey, "messageKey"));
    }

    /**
     * The title to render for the given message key: its own override if it has one, otherwise its
     * category's title, otherwise a plain default.
     */
    public @NotNull String titleFor(@NotNull String messageKey) {
        String override = this.titleOverrides.get(Objects.requireNonNull(messageKey, "messageKey"));
        if (override != null) {
            return override;
        }
        CategoryDefinition category = category(dataTypeFor(messageKey));
        String title = category == null ? "" : category.effectiveTitle();
        return title.isBlank() ? DEFAULT_TITLE : title;
    }

    /**
     * The delivery priority for the given message key's category; 0 when unconfigured.
     */
    public int priorityFor(@NotNull String messageKey) {
        CategoryDefinition category = category(dataTypeFor(messageKey));
        return category == null ? 0 : category.priority();
    }

    /** The preference-dialog label for a data type; the data type itself when it has no label. */
    public @NotNull String labelFor(@NotNull String dataType) {
        CategoryDefinition category = category(dataType);
        return category == null || category.label().isBlank() ? dataType : category.label();
    }

    /** The preference-dialog description for a data type; empty when it has none. */
    public @NotNull String descriptionFor(@NotNull String dataType) {
        CategoryDefinition category = category(dataType);
        return category == null ? "" : category.description();
    }

    private @Nullable CategoryDefinition category(@NotNull String dataType) {
        return this.categories.get(Objects.requireNonNull(dataType, "dataType"));
    }
}
