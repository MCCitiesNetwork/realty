package io.github.md5sha256.realty.adapter.playernotifs;

import io.github.md5sha256.playernotifications.api.render.RenderableNotification;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.UUID;

/**
 * Covers the title, which is the whole reason this renderer is more than a passthrough: PlayerNotifications'
 * inbox lists a row by its rendered title alone and shows the body only once the row is opened.
 */
class RealtyNotificationRendererTest {

    private static final RealtyNotificationRenderer RENDERER =
            new RealtyNotificationRenderer(TitleConfig.compiled());
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static String titleOf(RealtyNotificationPayload payload) {
        RenderableNotification rendered = RENDERER.render(payload, TARGET);
        return PlainTextComponentSerializer.plainText().serialize(rendered.title());
    }

    private static RealtyNotificationPayload payload(String messageKey, String regionId) {
        return RealtyNotificationPayload.of(messageKey,
                Component.text("the rendered message"),
                regionId,
                regionId == null ? null : UUID.randomUUID().toString());
    }

    /**
     * The bug this renderer was changed for: two lease notifications used to render the identical title
     * "Realty leases", making them indistinguishable in the inbox list.
     */
    @Test
    void twoKeysInOneCategoryRenderDistinctTitles() {
        String expired = titleOf(payload("notification.leasehold-expired", "plot42"));
        String rented = titleOf(payload("notification.region-rented", "plot42"));

        Assertions.assertNotEquals(expired, rented);
        Assertions.assertEquals("Lease expired — plot42", expired);
        Assertions.assertEquals("Region rented — plot42", rented);
    }

    /** The region is what separates two notifications of the same kind. */
    @Test
    void theRegionIsAppendedWhenThePayloadNamesOne() {
        Assertions.assertNotEquals(titleOf(payload("notification.outbid", "plot42")),
                titleOf(payload("notification.outbid", "plot7")));
    }

    /**
     * A payload that names no region — a refund announced after the region was deleted — still renders a
     * title, without a dangling separator.
     */
    @Test
    void aRegionlessPayloadRendersTheSummaryAlone() {
        Assertions.assertEquals("Outbid", titleOf(payload("notification.outbid", null)));
        Assertions.assertEquals("Outbid", titleOf(payload("notification.outbid", "")));
    }

    /** An unclaimed key keeps the old behaviour: the category label, never an empty title. */
    @Test
    void anUnclaimedKeyFallsBackToTheCategoryLabel() {
        Assertions.assertEquals(RealtyCategory.GENERAL.label() + " — plot42",
                titleOf(payload("notification.some-future-key", "plot42")));
    }

    /** An operator's override replaces the compiled summary; the region suffix is untouched by it. */
    @Test
    void anOperatorOverrideReplacesTheSummary() throws IOException {
        RealtyNotificationRenderer renderer;
        try (Reader reader = new StringReader("""
                titles:
                  notification.leasehold-expired: Your lease ran out
                """)) {
            renderer = new RealtyNotificationRenderer(TitleConfig.load(reader));
        }

        RenderableNotification rendered =
                renderer.render(payload("notification.leasehold-expired", "plot42"), TARGET);

        Assertions.assertEquals("Your lease ran out — plot42",
                PlainTextComponentSerializer.plainText().serialize(rendered.title()));
    }

    /** An override's colour survives into the rendered title, region suffix and all. */
    @Test
    void anOverridesColourSurvivesTheRegionSuffix() throws IOException {
        RealtyNotificationRenderer renderer;
        try (Reader reader = new StringReader("""
                titles:
                  notification.outbid: "<red>Outbid</red>"
                """)) {
            renderer = new RealtyNotificationRenderer(TitleConfig.load(reader));
        }

        RenderableNotification rendered =
                renderer.render(payload("notification.outbid", "plot42"), TARGET);

        Assertions.assertEquals("Outbid — plot42",
                PlainTextComponentSerializer.plainText().serialize(rendered.title()));
    }

    /**
     * The reload path: {@code /realty reload} swaps the title config on the renderer instance
     * PlayerNotifications already holds, so the very next render — of a payload stored long before
     * the edit — uses the new title.
     */
    @Test
    void swappingTheTitlesChangesSubsequentRenders() throws IOException {
        RealtyNotificationRenderer renderer =
                new RealtyNotificationRenderer(TitleConfig.compiled());
        RealtyNotificationPayload payload = payload("notification.leasehold-expired", "plot42");
        Assertions.assertEquals("Lease expired — plot42", PlainTextComponentSerializer.plainText()
                .serialize(renderer.render(payload, TARGET).title()));

        try (Reader reader = new StringReader("""
                titles:
                  notification.leasehold-expired: Your lease ran out
                """)) {
            renderer.setTitles(TitleConfig.load(reader));
        }

        Assertions.assertEquals("Your lease ran out — plot42", PlainTextComponentSerializer
                .plainText().serialize(renderer.render(payload, TARGET).title()));
    }

    /** The body is the payload's component verbatim; the title change must not have touched it. */
    @Test
    void theBodyIsThePayloadComponentVerbatim() {
        RenderableNotification rendered =
                RENDERER.render(payload("notification.leasehold-expired", "plot42"), TARGET);
        Assertions.assertEquals("the rendered message",
                PlainTextComponentSerializer.plainText().serialize(rendered.body()));
    }
}
