package io.github.md5sha256.realty.adapter.playernotifs;

import io.github.md5sha256.playernotifications.api.NotificationDataTypeRegistry;
import io.github.md5sha256.playernotifications.api.render.NotificationRenderer;
import io.github.md5sha256.playernotifications.api.render.RenderableNotification;
import io.github.md5sha256.playernotifications.api.serialize.PayloadSerializer;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Exercises registration and unregistration against a real {@link NotificationDataTypeRegistry} —
 * it is a plain concrete class with no Bukkit dependency, so no server is needed.
 */
class RegistrationLifecycleTest {

    private static final NotificationCategoryMapper MAPPER = TestCategories.defaults();

    private static final NotificationRenderer<RealtyNotificationPayload> RENDERER =
            new RealtyNotificationRenderer(MAPPER);

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
    private static NotificationDataTypeRegistry registerAll(NotificationCategoryMapper mapper) {
        NotificationDataTypeRegistry registry = new NotificationDataTypeRegistry();
        for (String dataType : mapper.dataTypes()) {
            registry.registerPayloadMapping(dataType, RealtyNotificationPayload.class);
            registry.registerSerializer(RealtyNotificationPayload.class, SERIALIZER);
            registry.registerRenderer(RealtyNotificationPayload.class, RENDERER);
        }
        return registry;
    }

    @Test
    void everyDeclaredDataTypeRegisters() {
        NotificationDataTypeRegistry registry = registerAll(MAPPER);

        for (String dataType : MAPPER.dataTypes()) {
            Assertions.assertTrue(registry.dataTypes().contains(dataType), dataType);
            Assertions.assertTrue(registry.getSerializer(dataType).isPresent(), dataType);
            Assertions.assertTrue(registry.getRenderer(dataType).isPresent(), dataType);
        }
        Assertions.assertEquals(MAPPER.dataTypes().size(), registry.dataTypes().size());
    }

    @Test
    void unregisteringTheWholeSetLeavesTheRegistryClean() {
        NotificationDataTypeRegistry registry = registerAll(MAPPER);

        RealtyDataTypes.unregisterAll(registry, MAPPER);

        Assertions.assertEquals(Map.of().keySet(), registry.dataTypes());
        Assertions.assertTrue(registry.getSerializer(RealtyNotificationPayload.class).isEmpty());
        Assertions.assertTrue(registry.getRenderer(RealtyNotificationPayload.class).isEmpty());
    }

    @Test
    void unregisteringTheWholeSetIsIdempotent() {
        NotificationDataTypeRegistry registry = registerAll(MAPPER);

        RealtyDataTypes.unregisterAll(registry, MAPPER);
        RealtyDataTypes.unregisterAll(registry, MAPPER);

        Assertions.assertTrue(registry.dataTypes().isEmpty());
    }

    @Test
    void reRegisteringOverAnExistingRegistrationIsIdempotent() {
        NotificationDataTypeRegistry registry = registerAll(MAPPER);

        for (String dataType : MAPPER.dataTypes()) {
            registry.registerPayloadMapping(dataType, RealtyNotificationPayload.class);
        }

        // Plain map puts, which is what makes `reloadable: true` safe for this module.
        Assertions.assertEquals(MAPPER.dataTypes().size(), registry.dataTypes().size());
        Assertions.assertTrue(registry.getRenderer("realty.auction").isPresent());
    }

    /**
     * Documents the PlayerNotifications footgun executably: every data type shares one payload
     * class, and the registry keys serializers and renderers by <em>class</em> while keying the
     * payload mapping by data type. Dropping one data type therefore rips the shared serializer and
     * renderer out from under the rest, which stay mapped but can no longer be serialized or
     * rendered. This is exactly why {@link RealtyDataTypes#unregisterAll} takes the whole set.
     */
    @Test
    void aPartialUnregisterSilentlyBreaksTheRemainingDataTypes() {
        NotificationDataTypeRegistry registry = registerAll(MAPPER);

        registry.unregisterPayloadMapping("realty.auction");

        Assertions.assertFalse(registry.dataTypes().contains("realty.auction"));
        Assertions.assertEquals(MAPPER.dataTypes().size() - 1, registry.dataTypes().size());
        for (String survivor : MAPPER.dataTypes()) {
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

    /**
     * The reload hazard the configurable category set introduces: if teardown used a mapper rebuilt
     * from an edited {@code categories.yml}, a category the operator deleted would be left
     * registered, mapped to a renderer on a class loader that is about to be closed. The module
     * therefore keeps the mapper it registered with — which is what this asserts.
     */
    @Test
    void tearingDownWithANewerMapperOrphansARemovedCategory() {
        NotificationDataTypeRegistry registry = registerAll(MAPPER);
        NotificationCategoryMapper afterOperatorDeletedAuctions = new NotificationCategoryMapper(
                List.of(TestCategories.category("realty.general", "Realty", "notification.region-bought")),
                Map.of(),
                "realty.general");

        RealtyDataTypes.unregisterAll(registry, afterOperatorDeletedAuctions);

        Assertions.assertTrue(registry.dataTypes().contains("realty.auction"),
                "realty.auction was registered but the newer mapper does not know to remove it");

        // The mapper that registered them removes them all.
        RealtyDataTypes.unregisterAll(registry, MAPPER);
        Assertions.assertTrue(registry.dataTypes().isEmpty());
    }

    @Test
    void theRendererProducesATitleAndTheVerbatimBody() {
        Component message = Component.text("You were outbid");
        RealtyNotificationPayload payload =
                RealtyNotificationPayload.of("notification.outbid", message, null, null);

        RenderableNotification rendered = RENDERER.render(payload, UUID.randomUUID());

        Assertions.assertEquals(message.compact(), rendered.body().compact());
        Assertions.assertEquals(Component.text("Realty — Auction"), rendered.title());
    }
}
