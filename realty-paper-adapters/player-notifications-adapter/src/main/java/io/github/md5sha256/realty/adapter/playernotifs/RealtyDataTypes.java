package io.github.md5sha256.realty.adapter.playernotifs;

import io.github.md5sha256.playernotifications.api.NotificationDataTypeRegistry;
import io.github.md5sha256.playernotifications.api.NotificationService;
import io.github.md5sha256.playernotifications.api.category.NotificationCategoryRegistry;
import io.github.md5sha256.playernotifications.api.render.NotificationRenderer;
import org.jetbrains.annotations.NotNull;

/**
 * Registers and unregisters the PlayerNotifications data types {@code categories.yml} declares.
 *
 * <p>Every method takes the {@link NotificationCategoryMapper} the data types came from, and no
 * method holds a list of its own. That is what makes the category set operator-configurable: adding
 * a category to the file adds it here, with its configured label and description, without a code
 * change.</p>
 *
 * <p><b>The shared payload class footgun.</b> All data types share one payload class,
 * {@link RealtyNotificationPayload}. {@code NotificationDataTypeRegistry} keys serializers and
 * renderers by payload <em>class</em> but the payload mapping by data type <em>string</em>, so
 * {@code unregisterPayloadMapping("realty.auction")} does not just drop that one mapping — it
 * cascades into {@code unregisterSerializer}/{@code unregisterRenderer} on the shared class,
 * silently leaving every other data type mapped but with no serializer and no renderer. Every
 * notification they carry then fails at enqueue or render time.</p>
 *
 * <p>Unregistering the whole set is therefore not a tidiness preference, it is the only correct
 * sequence: partially unregistering is what corrupts the registry. {@link
 * #unregisterAll(NotificationDataTypeRegistry, NotificationCategoryMapper)} exists so no call site
 * can get that wrong, and {@code RegistrationLifecycleTest} asserts the hazard so it stays
 * documented executably. This is a sharp edge in the PlayerNotifications API, not in this
 * module.</p>
 *
 * <p><b>Reloads must unregister the mapper they registered.</b> Because the set is now read from
 * config, a reload that removes or renames a category produces a mapper that no longer knows about
 * the data types actually in the registry. Passing the <em>new</em> mapper to
 * {@code unregisterAll} would leave those orphaned and mapped to a dead class loader's renderer.
 * {@code PlayerNotificationsAdapterModule} keeps the mapper it registered with and tears down with
 * that one.</p>
 */
public final class RealtyDataTypes {

    private RealtyDataTypes() {
    }

    /**
     * Binds every declared data type to {@link RealtyNotificationPayload} and claims it under a
     * category carrying its configured label and description.
     *
     * <p>Uses {@code registerJsonRenderable}, never {@code registerJsonPayload}: an explicit
     * processor wins dispatch precedence and bypasses preferences and sinks entirely, which would
     * defeat the whole reason for delivering through PN.</p>
     *
     * <p>Re-registration is idempotent — the underlying registries are plain map puts — which is
     * what makes this module safe to declare {@code reloadable: true}.</p>
     */
    public static void registerAll(@NotNull NotificationService service,
                                   @NotNull NotificationCategoryMapper mapper,
                                   @NotNull NotificationRenderer<RealtyNotificationPayload> renderer) {
        NotificationCategoryRegistry categories = service.categoryRegistry();
        for (String dataType : mapper.dataTypes()) {
            service.registerJsonRenderable(dataType, RealtyNotificationPayload.class, renderer);
            categories.registerCategory(dataType,
                    mapper.labelFor(dataType),
                    mapper.descriptionFor(dataType));
            categories.claimDataType(dataType, dataType);
        }
    }

    /**
     * Unregisters <em>every</em> data type the given mapper declares. See the class javadoc: doing
     * this partially corrupts the registry for the data types left behind.
     */
    public static void unregisterAll(@NotNull NotificationDataTypeRegistry registry,
                                     @NotNull NotificationCategoryMapper mapper) {
        for (String dataType : mapper.dataTypes()) {
            registry.unregisterPayloadMapping(dataType);
        }
        // The cascade above already removed the shared serializer and renderer, but say so
        // explicitly: if a future data type were ever given its own payload class, the loop alone
        // would no longer be enough, and these two calls are harmless no-ops today.
        registry.unregisterSerializer(RealtyNotificationPayload.class);
        registry.unregisterRenderer(RealtyNotificationPayload.class);
    }

    /**
     * Releases each category's claim on its data type.
     */
    public static void unclaimAll(@NotNull NotificationCategoryRegistry categories,
                                  @NotNull NotificationCategoryMapper mapper) {
        for (String dataType : mapper.dataTypes()) {
            categories.unclaimDataType(dataType, dataType);
        }
    }
}
