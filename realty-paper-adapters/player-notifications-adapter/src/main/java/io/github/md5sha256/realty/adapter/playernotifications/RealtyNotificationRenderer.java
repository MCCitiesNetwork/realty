package io.github.md5sha256.realty.adapter.playernotifications;

import io.github.md5sha256.playernotifications.api.render.NotificationRenderer;
import io.github.md5sha256.playernotifications.api.render.RenderableNotification;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;

/**
 * Renders a stored {@link RealtyNotificationPayload} back into the medium-neutral title/body form
 * PlayerNotifications fans out to whichever sinks the recipient prefers.
 *
 * <p>The body is the payload's component deserialized verbatim — Realty already rendered the text
 * at the fire site, so there is nothing left to decide here. The title comes from module config
 * via {@link NotificationCategoryMapper}, keyed by data type with a per-message-key override.</p>
 *
 * <p>Rendering ignores the target: Realty's messages are already per-target (several targets means
 * several people get the <em>same</em> text), so there is nothing to personalise.</p>
 *
 * <p>Neither title nor body may depend on click events to be understood. Sinks that are not
 * Minecraft clients — Essentials mail, Discord — flatten components to plain text and are free to
 * drop interaction entirely.</p>
 */
public final class RealtyNotificationRenderer implements NotificationRenderer<RealtyNotificationPayload> {

    private final NotificationCategoryMapper categoryMapper;

    public RealtyNotificationRenderer(@NotNull NotificationCategoryMapper categoryMapper) {
        this.categoryMapper = Objects.requireNonNull(categoryMapper, "categoryMapper");
    }

    @Override
    public @NotNull RenderableNotification render(@NotNull RealtyNotificationPayload payload,
                                                  @NotNull UUID target) {
        Component title = Component.text(this.categoryMapper.titleFor(payload.messageKey()));
        return new RenderableNotification(title, payload.bodyComponent());
    }
}
