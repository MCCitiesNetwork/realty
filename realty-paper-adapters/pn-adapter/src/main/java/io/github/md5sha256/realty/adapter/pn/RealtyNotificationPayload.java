package io.github.md5sha256.realty.adapter.pn;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * The persisted form of a Realty notification inside PlayerNotifications.
 *
 * <p>One payload class serves all of Realty's data types ({@code realty.auction},
 * {@code realty.offer}, {@code realty.lease}, {@code realty.agent}, {@code realty.general}) —
 * only the routing label differs, so sharing the serializer and renderer across them is exactly
 * the intent. See {@link PlayerNotificationsAdapterModule} for the shutdown consequence of that
 * sharing.</p>
 *
 * <p>{@link #body} is a GSON-serialized {@link Component}, not MiniMessage. The event hands the
 * adapter an already-built {@code Component}; a MiniMessage round-trip is lossy for components
 * assembled programmatically (hover and click events, insertions, custom fonts), whereas the GSON
 * form is the same tree the server itself sends over the wire.</p>
 *
 * <p>Region identity is carried as plain strings rather than as a live region handle: the payload
 * is persisted and routinely outlives the region it describes — a refund is announced after the
 * region has already been deleted — so both {@code regionId} and {@code worldId} being null is
 * routine, not a defect.</p>
 *
 * @param messageKey the {@code messages.yml} path the notification was rendered from; provenance
 *                   and the source of the rendered title
 * @param body       the rendered message, serialized with {@link GsonComponentSerializer}
 * @param regionId   the WorldGuard region id, or null when the notification names no region
 * @param worldId    the region's world UUID as a string, or null alongside a null {@code regionId}
 */
public record RealtyNotificationPayload(@NotNull String messageKey,
                                        @NotNull String body,
                                        @Nullable String regionId,
                                        @Nullable String worldId) {

    public RealtyNotificationPayload {
        Objects.requireNonNull(messageKey, "messageKey");
        Objects.requireNonNull(body, "body");
        if (messageKey.isBlank()) {
            throw new IllegalArgumentException("messageKey must not be blank");
        }
    }

    /**
     * Builds a payload from a rendered component, serializing it to the GSON form.
     */
    public static @NotNull RealtyNotificationPayload of(@NotNull String messageKey,
                                                        @NotNull Component message,
                                                        @Nullable String regionId,
                                                        @Nullable String worldId) {
        return new RealtyNotificationPayload(messageKey,
                GsonComponentSerializer.gson().serialize(message),
                regionId,
                worldId);
    }

    /**
     * The message as a {@link Component} again, deserialized verbatim from {@link #body}.
     */
    public @NotNull Component bodyComponent() {
        return GsonComponentSerializer.gson().deserialize(this.body);
    }
}
