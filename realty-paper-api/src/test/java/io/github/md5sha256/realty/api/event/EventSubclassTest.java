package io.github.md5sha256.realty.api.event;

import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

class EventSubclassTest {

    private static final Component MESSAGE = Component.text("rendered");
    private static final String REGION = "plot_1";

    @Test
    void regionBoughtEventCarriesDomainFields() {
        UUID target = UUID.randomUUID();
        UUID world = UUID.randomUUID();
        UUID buyer = UUID.randomUUID();

        RegionBoughtEvent event =
                new RegionBoughtEvent(target, MESSAGE, REGION, world, buyer, "Notch", 250.0);

        Assertions.assertEquals(List.of(target), event.targetIds());
        Assertions.assertEquals(buyer, event.buyerId());
        Assertions.assertEquals("Notch", event.buyerName());
        Assertions.assertEquals(250.0, event.price());
    }

    @Test
    void offerRejectedEventAcceptsMultipleTargets() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        OfferRejectedEvent event = new OfferRejectedEvent(List.of(first, second), MESSAGE, REGION,
                UUID.randomUUID());

        Assertions.assertEquals(List.of(first, second), event.targetIds());
    }

    @Test
    void everySubclassSharesTheBaseHandlerList() {
        UUID target = UUID.randomUUID();
        UUID world = UUID.randomUUID();

        List<RealtyNotificationEvent> events = List.of(
                new RegionBoughtEvent(target, MESSAGE, REGION, world, target, "Notch", 1.0),
                new OutbidEvent(target, MESSAGE, REGION, world, target, 5.0),
                new AgentRemovedEvent(target, MESSAGE, REGION, world, target, "Notch", target),
                new LeaseholdExpiredEvent(target, MESSAGE, REGION, world, target, target));

        for (RealtyNotificationEvent event : events) {
            Assertions.assertSame(RealtyNotificationEvent.getHandlerList(), event.getHandlers());
        }
    }
}
