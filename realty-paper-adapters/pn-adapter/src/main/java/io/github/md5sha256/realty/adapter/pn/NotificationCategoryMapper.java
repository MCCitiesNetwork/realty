package io.github.md5sha256.realty.adapter.pn;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Resolves a Realty message key to the PlayerNotifications {@code dataType} it is enqueued under,
 * and to the title and priority that data type is rendered with.
 *
 * <p>Deliberately a plain class with no PlayerNotifications and no Bukkit types on it: the routing
 * decision is the part worth testing, and keeping it free of both means it can be tested without a
 * server or a live PN install.</p>
 *
 * <p>An unrecognised key resolves to {@link #FALLBACK_DATA_TYPE} rather than throwing or dropping.
 * Realty adds message keys over time and third-party fire sites may use keys of their own; a
 * notification the mapper has never seen is still a notification a player should receive.</p>
 */
public final class NotificationCategoryMapper {

    /** The data type an unmapped message key routes to. */
    public static final String FALLBACK_DATA_TYPE = "realty.general";

    /** Every data type this adapter registers, in a stable order. */
    public static final List<String> DATA_TYPES = List.of(
            "realty.auction",
            "realty.offer",
            "realty.lease",
            "realty.agent",
            "realty.general");

    private static final String DEFAULT_TITLE = "Realty";

    private final Map<String, String> keyToDataType;
    private final Map<String, String> dataTypeTitles;
    private final Map<String, String> titleOverrides;
    private final Map<String, Integer> priorities;

    /**
     * @param keyToDataType  message key to data type; unlisted keys fall back
     * @param dataTypeTitles data type to display title
     * @param titleOverrides message key to display title, beating {@code dataTypeTitles}
     * @param priorities     data type to delivery priority; unlisted data types get 0
     */
    public NotificationCategoryMapper(@NotNull Map<String, String> keyToDataType,
                                      @NotNull Map<String, String> dataTypeTitles,
                                      @NotNull Map<String, String> titleOverrides,
                                      @NotNull Map<String, Integer> priorities) {
        this.keyToDataType = Map.copyOf(Objects.requireNonNull(keyToDataType, "keyToDataType"));
        this.dataTypeTitles = Map.copyOf(Objects.requireNonNull(dataTypeTitles, "dataTypeTitles"));
        this.titleOverrides = Map.copyOf(Objects.requireNonNull(titleOverrides, "titleOverrides"));
        this.priorities = Map.copyOf(Objects.requireNonNull(priorities, "priorities"));
    }

    /**
     * The data type the given message key routes to, or {@link #FALLBACK_DATA_TYPE} if the key is
     * not mapped.
     */
    public @NotNull String dataTypeFor(@NotNull String messageKey) {
        Objects.requireNonNull(messageKey, "messageKey");
        return this.keyToDataType.getOrDefault(messageKey, FALLBACK_DATA_TYPE);
    }

    /**
     * Whether the given message key is explicitly mapped. Callers use this to log the fallback,
     * because {@link #dataTypeFor} cannot distinguish an unmapped key from one deliberately mapped
     * to {@link #FALLBACK_DATA_TYPE}.
     */
    public boolean isMapped(@NotNull String messageKey) {
        return this.keyToDataType.containsKey(Objects.requireNonNull(messageKey, "messageKey"));
    }

    /**
     * The title to render for the given message key: its own override if it has one, otherwise the
     * title of its data type, otherwise a plain default.
     */
    public @NotNull String titleFor(@NotNull String messageKey) {
        String override = this.titleOverrides.get(Objects.requireNonNull(messageKey, "messageKey"));
        if (override != null) {
            return override;
        }
        return this.dataTypeTitles.getOrDefault(dataTypeFor(messageKey), DEFAULT_TITLE);
    }

    /**
     * The delivery priority for the given message key's data type; 0 when unconfigured.
     */
    public int priorityFor(@NotNull String messageKey) {
        return this.priorities.getOrDefault(dataTypeFor(messageKey), 0);
    }
}
