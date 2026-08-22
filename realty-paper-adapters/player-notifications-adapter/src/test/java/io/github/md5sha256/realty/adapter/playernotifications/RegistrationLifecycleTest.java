package io.github.md5sha256.realty.adapter.playernotifications;

import io.github.md5sha256.playernotifications.api.NotificationDataTypeRegistry;
import io.github.md5sha256.playernotifications.api.render.NotificationRenderer;
import io.github.md5sha256.playernotifications.api.render.RenderableNotification;
import io.github.md5sha256.playernotifications.api.serialize.PayloadSerializer;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

/**
 * Exercises registration and unregistration against a real {@link NotificationDataTypeRegistry} —
 * it is a plain concrete class with no Bukkit dependency, so no server is needed.
 */
class RegistrationLifecycleTest {

    private static final NotificationRenderer<RealtyNotificationPayload> RENDERER =
            new RealtyNotificationRenderer(new NotificationCategoryMapper(
                    Map.of(), Map.of(), Map.of(), Map.of()));

    /**
     * Stands in for the reflective JSON serializer {@code registerJsonRenderable} installs; only its
     * presence or absence in the registry is under test.
     */
    private static final PayloadSerializer<RealtyNotificationPayload> SERIALIZER =
            new PayloadSerializer<>() {
                @Override
                public @NotNull String serialize(@NotNull RealtyNotificationPayload payload) {
                    return payload.body();
                }

                @Override
                public @NotNull RealtyNotificationPayload deserialize(@NotNull String json) {
                    return new RealtyNotificationPayload("notification.outbid", json, null, null);
                }
            };

    /**
     * Mirrors what {@code NotificationService.registerJsonRenderable} does to the registry: bind the
     * data type to the payload class, and register a serializer and renderer for that class.
     */
    private static NotificationDataTypeRegistry registerAllFive() {
        NotificationDataTypeRegistry registry = new NotificationDataTypeRegistry();
        for (String dataType : NotificationCategoryMapper.DATA_TYPES) {
            registry.registerPayloadMapping(dataType, RealtyNotificationPayload.class);
            registry.registerSerializer(RealtyNotificationPayload.class, SERIALIZER);
            registry.registerRenderer(RealtyNotificationPayload.class, RENDERER);
        }
        return registry;
    }

    @Test
    void allFiveDataTypesRegister() {
        NotificationDataTypeRegistry registry = registerAllFive();

        for (String dataType : NotificationCategoryMapper.DATA_TYPES) {
            Assertions.assertTrue(registry.dataTypes().contains(dataType), dataType);
            Assertions.assertTrue(registry.getSerializer(dataType).isPresent(), dataType);
            Assertions.assertTrue(registry.getRenderer(dataType).isPresent(), dataType);
        }
        Assertions.assertEquals(5, registry.dataTypes().size());
    }

    @Test
    void unregisteringAllFiveLeavesTheRegistryClean() {
        NotificationDataTypeRegistry registry = registerAllFive();

        RealtyDataTypes.unregisterAll(registry);

        Assertions.assertEquals(Map.of().keySet(), registry.dataTypes());
        Assertions.assertTrue(registry.getSerializer(RealtyNotificationPayload.class).isEmpty());
        Assertions.assertTrue(registry.getRenderer(RealtyNotificationPayload.class).isEmpty());
    }

    @Test
    void unregisteringAllFiveIsIdempotent() {
        NotificationDataTypeRegistry registry = registerAllFive();

        RealtyDataTypes.unregisterAll(registry);
        RealtyDataTypes.unregisterAll(registry);

        Assertions.assertTrue(registry.dataTypes().isEmpty());
    }

    @Test
    void reRegisteringOverAnExistingRegistrationIsIdempotent() {
        NotificationDataTypeRegistry registry = registerAllFive();

        for (String dataType : NotificationCategoryMapper.DATA_TYPES) {
            registry.registerPayloadMapping(dataType, RealtyNotificationPayload.class);
        }

        // Plain map puts, which is what makes `reloadable: true` safe for this module.
        Assertions.assertEquals(5, registry.dataTypes().size());
        Assertions.assertTrue(registry.getRenderer("realty.auction").isPresent());
    }

    /**
     * Documents the PlayerNotifications footgun executably: all five data types share one payload
     * class, and the registry keys serializers and renderers by <em>class</em> while keying the
     * payload mapping by data type. Dropping one data type therefore rips the shared serializer and
     * renderer out from under the other four, which stay mapped but can no longer be serialized or
     * rendered. This is exactly why {@link RealtyDataTypes#unregisterAll} unregisters all five.
     */
    @Test
    void aPartialUnregisterSilentlyBreaksTheOtherFourDataTypes() {
        NotificationDataTypeRegistry registry = registerAllFive();

        registry.unregisterPayloadMapping("realty.auction");

        Assertions.assertFalse(registry.dataTypes().contains("realty.auction"));
        Assertions.assertEquals(4, registry.dataTypes().size());
        for (String survivor : NotificationCategoryMapper.DATA_TYPES) {
            if (survivor.equals("realty.auction")) {
                continue;
            }
            Assertions.assertTrue(registry.dataTypes().contains(survivor), survivor);
            Assertions.assertTrue(registry.getSerializer(survivor).isEmpty(),
                    survivor + " lost its serializer to the shared-class cascade");
            Assertions.assertTrue(registry.getRenderer(survivor).isEmpty(),
                    survivor + " lost its renderer to the shared-class cascade");
        }
    }

    @Test
    void theRendererProducesATitleAndTheVerbatimBody() {
        Component message = Component.text("You were outbid");
        RealtyNotificationPayload payload =
                RealtyNotificationPayload.of("notification.outbid", message, null, null);

        RenderableNotification rendered = RENDERER.render(payload, UUID.randomUUID());

        Assertions.assertEquals(message.compact(), rendered.body().compact());
        Assertions.assertEquals(Component.text("Realty"), rendered.title());
    }
}
