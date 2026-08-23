package io.github.md5sha256.realty.adapter.playernotifs;

import io.github.md5sha256.playernotifications.api.render.NotificationRenderer;
import io.github.md5sha256.playernotifications.api.render.RenderableNotification;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Renders a stored {@link RealtyNotificationPayload} back into the medium-neutral title/body form
 * PlayerNotifications fans out to whichever sinks the recipient prefers.
 *
 * <p>The body is the payload's component deserialized verbatim — Realty already rendered the text at
 * the fire site, so there is nothing left to decide here. The title is the message key's category
 * label.</p>
 *
 * <p><b>The title is the registered label, not the operator's.</b> An operator who renames a category
 * in PlayerNotifications' {@code categories.yml} changes what the preference dialogs show, but not
 * this title: the merged label lives in PN's core and is not on the API this module compiles against.
 * Presenting the operator's name here is PlayerNotifications' problem to solve, and when it does this
 * renderer follows it rather than growing a title config of its own.</p>
 *
 * <p>Rendering ignores the target: Realty's messages are already per-target (several targets means
 * several people get the <em>same</em> text), so there is nothing to personalise.</p>
 *
 * <p>Neither title nor body may depend on click events to be understood. Sinks that are not Minecraft
 * clients — Essentials mail, Discord — flatten components to plain text and are free to drop
 * interaction entirely.</p>
 */
public final class RealtyNotificationRenderer implements NotificationRenderer<RealtyNotificationPayload> {

    @Override
    public @NotNull RenderableNotification render(@NotNull RealtyNotificationPayload payload,
                                                  @NotNull UUID target) {
        Component title = Component.text(RealtyCategory.forMessageKey(payload.messageKey()).label());
        return new RenderableNotification(title, payload.bodyComponent());
    }
}
