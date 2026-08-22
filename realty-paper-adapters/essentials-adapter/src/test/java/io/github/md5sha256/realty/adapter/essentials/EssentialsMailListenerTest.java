package io.github.md5sha256.realty.adapter.essentials;

import io.github.md5sha256.realty.api.event.RealtyNotificationEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

class EssentialsMailListenerTest {

    private static final String KEY = "notification.offer.rejected";

    @Test
    void mailsOfflineTargets() {
        UUID offline = UUID.randomUUID();
        List<Map.Entry<UUID, String>> sent = new ArrayList<>();

        EssentialsMailListener listener = new EssentialsMailListener(
                (uuid, text) -> sent.add(Map.entry(uuid, text)),
                uuid -> false);
        RealtyNotificationEvent event = new RealtyNotificationEvent(
                List.of(offline), KEY, Component.text("rejected"), null);

        listener.onNotification(event);

        Assertions.assertEquals(1, sent.size());
        Assertions.assertEquals(offline, sent.get(0).getKey());
        Assertions.assertEquals("rejected", sent.get(0).getValue());
    }

    @Test
    void onlineTargetIsNotMailed() {
        List<Map.Entry<UUID, String>> sent = new ArrayList<>();

        EssentialsMailListener listener = new EssentialsMailListener(
                (uuid, text) -> sent.add(Map.entry(uuid, text)),
                uuid -> true);
        RealtyNotificationEvent event = new RealtyNotificationEvent(
                List.of(UUID.randomUUID()), KEY, Component.text("rejected"), null);

        listener.onNotification(event);

        Assertions.assertEquals(List.of(), sent);
    }

    @Test
    void messageIsSerializedToLegacySection() {
        List<Map.Entry<UUID, String>> sent = new ArrayList<>();

        EssentialsMailListener listener = new EssentialsMailListener(
                (uuid, text) -> sent.add(Map.entry(uuid, text)),
                uuid -> false);
        RealtyNotificationEvent event = new RealtyNotificationEvent(
                List.of(UUID.randomUUID()), KEY, Component.text("sold", NamedTextColor.RED), null);

        listener.onNotification(event);

        Assertions.assertEquals("§csold", sent.get(0).getValue());
    }

    @Test
    void aFailingSendDoesNotStopRemainingTargets() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        Set<UUID> delivered = new HashSet<>();

        EssentialsMailListener listener = new EssentialsMailListener(
                (uuid, text) -> {
                    if (uuid.equals(first)) {
                        throw new IllegalStateException("no such user");
                    }
                    delivered.add(uuid);
                },
                uuid -> false);
        RealtyNotificationEvent event = new RealtyNotificationEvent(
                List.of(first, second), KEY, Component.text("rejected"), null);

        listener.onNotification(event);

        Assertions.assertEquals(Set.of(second), delivered);
    }
}
