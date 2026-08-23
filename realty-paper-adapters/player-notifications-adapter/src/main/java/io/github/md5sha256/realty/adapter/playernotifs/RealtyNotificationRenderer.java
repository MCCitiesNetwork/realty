package io.github.md5sha256.realty.adapter.playernotifs;

import io.github.md5sha256.playernotifications.api.render.NotificationRenderer;
import io.github.md5sha256.playernotifications.api.render.RenderableNotification;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Renders a stored {@link RealtyNotificationPayload} back into the medium-neutral title/body form
 * PlayerNotifications fans out to whichever sinks the recipient prefers.
 *
 * <p>The body is the payload's component deserialized verbatim — Realty already rendered the text at
 * the fire site, so there is nothing left to decide here.</p>
 *
 * <p><b>The title must identify the individual notification, not its category.</b> PN's inbox lists a
 * row by its rendered title alone and reveals the body only when the row is opened, so a title that is
 * constant per category gives a player a screen of identical rows — fourteen lease notifications all
 * reading "Realty leases" — with no way to tell a rent payment from an eviction without opening each.
 * The title is therefore the message key's own summary suffixed with the region when the payload
 * names one, which is what makes two notifications of the <em>same</em> kind distinguishable from
 * each other.</p>
 *
 * <p>Where that summary comes from is the operator's call: {@link TitleConfig} answers with their
 * {@code titles.yml} override if they wrote one and {@link RealtyCategory#titleFor} otherwise, so
 * this class never needs to know which.</p>
 *
 * <p>That config is held in an {@link AtomicReference} so {@code /realty reload} can swap it under a
 * renderer PlayerNotifications already holds a reference to. Re-registering the renderer instead
 * would mean an {@code unregisterAll}/{@code registerAll} cycle on PN's registry, which
 * {@link RealtyDataTypes} documents as a sharp edge; swapping the field avoids touching the registry
 * at all. A render racing the swap reads either the old or the new config, never a torn one.</p>
 *
 * <p>The region is appended as its raw WorldGuard id. That is what the body already shows and what the
 * player types into commands; resolving a friendlier name would need a live region the payload
 * deliberately does not hold — it routinely outlives the region it describes.</p>
 *
 * <p>Rendering ignores the target: Realty's messages are already per-target (several targets means
 * several people get the <em>same</em> text), so there is nothing to personalise.</p>
 *
 * <p>Neither title nor body may depend on click events to be understood. Sinks that are not Minecraft
 * clients — Essentials mail, Discord — flatten components to plain text and are free to drop
 * interaction entirely.</p>
 */
public final class RealtyNotificationRenderer implements NotificationRenderer<RealtyNotificationPayload> {

    private final AtomicReference<TitleConfig> titles;

    public RealtyNotificationRenderer(@NotNull TitleConfig titles) {
        this.titles = new AtomicReference<>(Objects.requireNonNull(titles, "titles"));
    }

    /**
     * Replaces the row titles used by every subsequent render, including of notifications already
     * sitting in a player's inbox — PlayerNotifications renders a stored payload at display time.
     *
     * @param titles the freshly read {@code titles.yml}
     */
    public void setTitles(@NotNull TitleConfig titles) {
        this.titles.set(Objects.requireNonNull(titles, "titles"));
    }

    @Override
    public @NotNull RenderableNotification render(@NotNull RealtyNotificationPayload payload,
                                                  @NotNull UUID target) {
        Component summary = this.titles.get().titleFor(payload.messageKey());
        String regionId = payload.regionId();
        Component title = regionId == null || regionId.isBlank()
                ? summary
                : summary.append(Component.text(" — " + regionId));
        return new RenderableNotification(title, payload.bodyComponent());
    }
}
