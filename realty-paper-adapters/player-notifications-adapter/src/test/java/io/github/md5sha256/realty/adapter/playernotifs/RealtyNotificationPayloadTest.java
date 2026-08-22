package io.github.md5sha256.realty.adapter.playernotifs;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class RealtyNotificationPayloadTest {

    @Test
    void aColouredComponentSurvivesTheRoundTrip() {
        Component message = Component.text("You were outbid", NamedTextColor.RED);

        RealtyNotificationPayload payload =
                RealtyNotificationPayload.of("notification.outbid", message, "plot1", "world-uuid");

        Assertions.assertEquals(message.compact(), payload.bodyComponent().compact());
    }

    @Test
    void aProgrammaticallyBuiltComponentKeepsItsHoverAndClick() {
        Component message = Component.text("Region ")
                .append(Component.text("plot1", NamedTextColor.GOLD)
                        .decorate(TextDecoration.UNDERLINED)
                        .hoverEvent(HoverEvent.showText(Component.text("Click to view")))
                        .clickEvent(ClickEvent.runCommand("/realty info plot1")))
                .append(Component.text(" was sold."));

        RealtyNotificationPayload payload =
                RealtyNotificationPayload.of("notification.region-bought", message, "plot1", "world-uuid");
        Component restored = payload.bodyComponent();

        // This is the executable form of the "MiniMessage would be lossy" decision: the GSON form
        // preserves the whole tree, hover and click events included.
        Assertions.assertEquals(message.compact(), restored.compact());
        Component regionPart = restored.children().get(0);
        Assertions.assertEquals(ClickEvent.runCommand("/realty info plot1"), regionPart.clickEvent());
        Assertions.assertNotNull(regionPart.hoverEvent());
        Assertions.assertEquals(NamedTextColor.GOLD, regionPart.color());
    }

    @Test
    void aNullRegionAndWorldAreCarriedAsNull() {
        Component message = Component.text("Your bid was refunded.");

        RealtyNotificationPayload payload =
                RealtyNotificationPayload.of("notification.bid-payment-expired", message, null, null);

        Assertions.assertNull(payload.regionId());
        Assertions.assertNull(payload.worldId());
        Assertions.assertEquals(message.compact(), payload.bodyComponent().compact());
    }

    @Test
    void aBlankMessageKeyIsRejected() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new RealtyNotificationPayload("  ", "{}", null, null));
    }
}
