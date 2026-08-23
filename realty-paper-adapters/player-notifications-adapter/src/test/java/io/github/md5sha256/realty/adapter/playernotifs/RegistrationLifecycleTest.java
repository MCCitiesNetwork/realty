package io.github.md5sha256.realty.adapter.playernotifs;

import io.github.md5sha256.playernotifications.api.NotificationDataTypeRegistry;
import io.github.md5sha256.playernotifications.api.category.DefaultNotificationCategoryRegistry;
import io.github.md5sha256.playernotifications.api.category.NotificationCategoryRegistry;
import io.github.md5sha256.playernotifications.api.render.NotificationRenderer;
import io.github.md5sha256.playernotifications.api.render.RenderableNotification;
import io.github.md5sha256.playernotifications.api.serialize.PayloadSerializer;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Exercises registration and unregistration against a real {@link NotificationDataTypeRegistry} and
 * {@link DefaultNotificationCategoryRegistry} — both are plain concrete classes with no Bukkit
 * dependency, so no server is needed.
 */
class RegistrationLifecycleTest {

    private static final NotificationRenderer<RealtyNotificationPayload> RENDERER =
            new RealtyNotificationRenderer();

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
    private static NotificationDataTypeRegistry registerAll() {
        NotificationDataTypeRegistry registry = new NotificationDataTypeRegistry();
        for (RealtyCategory category : RealtyCategory.values()) {
            registry.registerPayloadMapping(category.dataType(), RealtyNotificationPayload.class);
            registry.registerSerializer(RealtyNotificationPayload.class, SERIALIZER);
            registry.registerRenderer(RealtyNotificationPayload.class, RENDERER);
            registry.registerDisplayName(category.dataType(), category.label());
        }
        return registry;
    }

    @Test
    void everyDeclaredDataTypeRegisters() {
        NotificationDataTypeRegistry registry = registerAll();

        for (RealtyCategory category : RealtyCategory.values()) {
            String dataType = category.dataType();
            Assertions.assertTrue(registry.dataTypes().contains(dataType), dataType);
            Assertions.assertTrue(registry.getSerializer(dataType).isPresent(), dataType);
            Assertions.assertTrue(registry.getRenderer(dataType).isPresent(), dataType);
        }
        Assertions.assertEquals(RealtyCategory.values().length, registry.dataTypes().size());
    }

    /**
     * Every data type carries a name, so the preference screens never fall through to PN title-casing
     * the registry key — which would read "Realty.auction".
     */
    @Test
    void everyDataTypeIsRegisteredWithADisplayName() {
        NotificationDataTypeRegistry registry = registerAll();

        for (RealtyCategory category : RealtyCategory.values()) {
            Assertions.assertEquals(Optional.of(category.label()),
                    registry.displayName(category.dataType()), category.dataType());
        }
    }

    /**
     * A display name is keyed by data type while the serializer and renderer are keyed by payload
     * class, so it is not swept up by the payload-mapping cascade — teardown must drop it explicitly
     * or a reloaded module leaves its names behind.
     */
    @Test
    void unregisteringDropsTheDisplayNamesToo() {
        NotificationDataTypeRegistry registry = registerAll();

        RealtyDataTypes.unregisterAll(registry);

        for (RealtyCategory category : RealtyCategory.values()) {
            Assertions.assertTrue(registry.displayName(category.dataType()).isEmpty(),
                    category.dataType());
        }
    }

    @Test
    void unregisteringTheWholeSetLeavesTheRegistryClean() {
        NotificationDataTypeRegistry registry = registerAll();

        RealtyDataTypes.unregisterAll(registry);

        Assertions.assertTrue(registry.dataTypes().isEmpty());
        Assertions.assertTrue(registry.getSerializer(RealtyNotificationPayload.class).isEmpty());
        Assertions.assertTrue(registry.getRenderer(RealtyNotificationPayload.class).isEmpty());
    }

    @Test
    void unregisteringTheWholeSetIsIdempotent() {
        NotificationDataTypeRegistry registry = registerAll();

        RealtyDataTypes.unregisterAll(registry);
        RealtyDataTypes.unregisterAll(registry);

        Assertions.assertTrue(registry.dataTypes().isEmpty());
    }

    @Test
    void reRegisteringOverAnExistingRegistrationIsIdempotent() {
        NotificationDataTypeRegistry registry = registerAll();

        for (RealtyCategory category : RealtyCategory.values()) {
            registry.registerPayloadMapping(category.dataType(), RealtyNotificationPayload.class);
        }

        // Plain map puts, which is what makes `reloadable: true` safe for this module.
        Assertions.assertEquals(RealtyCategory.values().length, registry.dataTypes().size());
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
        NotificationDataTypeRegistry registry = registerAll();

        registry.unregisterPayloadMapping("realty.auction");

        Assertions.assertFalse(registry.dataTypes().contains("realty.auction"));
        Assertions.assertEquals(RealtyCategory.values().length - 1, registry.dataTypes().size());
        for (RealtyCategory category : RealtyCategory.values()) {
            String survivor = category.dataType();
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
     * Each category registers with its compiled default label and description and claims exactly its
     * own data type — the blocks an operator then sees in the generated
     * {@code categories-defaults.yml}.
     */
    @Test
    void categoriesRegisterWithTheirDefaultsAndClaimTheirOwnDataType() {
        NotificationCategoryRegistry categories = new DefaultNotificationCategoryRegistry();

        for (RealtyCategory category : RealtyCategory.values()) {
            categories.registerCategory(
                    category.dataType(), category.label(), category.description());
            categories.claimDataType(category.dataType(), category.dataType());
        }

        for (RealtyCategory category : RealtyCategory.values()) {
            String key = category.dataType();
            Assertions.assertEquals(category.label(), categories.label(key));
            Assertions.assertEquals(category.description(), categories.description(key));
            Assertions.assertEquals(Set.of(key), categories.dataTypesFor(key));
        }
    }

    @Test
    void unclaimingReleasesEveryCategoryClaim() {
        NotificationCategoryRegistry categories = new DefaultNotificationCategoryRegistry();
        for (RealtyCategory category : RealtyCategory.values()) {
            categories.claimDataType(category.dataType(), category.dataType());
        }

        RealtyDataTypes.unclaimAll(categories);

        for (RealtyCategory category : RealtyCategory.values()) {
            Assertions.assertEquals(Set.of(), categories.dataTypesFor(category.dataType()),
                    category.dataType());
        }
    }

    @Test
    void theRendererProducesATitleAndTheVerbatimBody() {
        Component message = Component.text("You were outbid");
        RealtyNotificationPayload payload =
                RealtyNotificationPayload.of("notification.outbid", message, null, null);

        RenderableNotification rendered = RENDERER.render(payload, UUID.randomUUID());

        Assertions.assertEquals(message.compact(), rendered.body().compact());
        // The message key's own summary, not the category label — see RealtyNotificationRendererTest.
        Assertions.assertEquals(Component.text("Outbid"), rendered.title());
    }
}
