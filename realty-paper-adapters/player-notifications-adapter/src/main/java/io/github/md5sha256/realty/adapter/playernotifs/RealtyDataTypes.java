package io.github.md5sha256.realty.adapter.playernotifs;

import io.github.md5sha256.playernotifications.api.NotificationDataTypeRegistry;
import io.github.md5sha256.playernotifications.api.NotificationService;
import io.github.md5sha256.playernotifications.api.category.NotificationCategoryRegistry;
import io.github.md5sha256.playernotifications.api.render.NotificationRenderer;
import org.jetbrains.annotations.NotNull;

/**
 * Registers and unregisters the PlayerNotifications data types {@link RealtyCategory} declares.
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
 * #unregisterAll(NotificationDataTypeRegistry)} exists so no call site can get that wrong, and
 * {@code RegistrationLifecycleTest} asserts the hazard so it stays documented executably. This is a
 * sharp edge in the PlayerNotifications API, not in this module.</p>
 *
 * <p><b>Registering late is fine.</b> A module starts from Realty's {@code onEnable}, well after
 * PlayerNotifications has built its merged category snapshot. PN's registry fires
 * {@code addChangeListener} on every mutation and PN rebuilds, so these categories reach the
 * preference dialogs without this module having to know it was late.</p>
 */
public final class RealtyDataTypes {

    private RealtyDataTypes() {
    }

    /**
     * Binds every category's data type to {@link RealtyNotificationPayload}, names it, and registers
     * the category with its default label and description.
     *
     * <p>The category claims exactly one data type — its own — so a player toggling a category in
     * {@code /notifications preferences} toggles precisely the Realty notifications it routes. An
     * operator who wants a different grouping regroups these data types in PlayerNotifications'
     * {@code categories.yml}; nothing here needs to change for that.</p>
     *
     * <p>The display name is the category's label, registered with the data type rather than left to
     * PlayerNotifications to guess: without one PN title-cases the registry key, and
     * {@code realty.auction} title-cases to "Realty.auction". Like the label, it is a default — an
     * operator's entry in PN's {@code type-names.yml} wins, and may carry MiniMessage colour these
     * plain labels do not.</p>
     *
     * <p>Uses {@code registerJsonRenderable}, never {@code registerJsonPayload}: an explicit
     * processor wins dispatch precedence and bypasses preferences and sinks entirely, which would
     * defeat the whole reason for delivering through PN.</p>
     *
     * <p>Re-registration is idempotent — the underlying registries are plain map puts — which is
     * what makes this module safe to declare {@code reloadable: true}.</p>
     */
    public static void registerAll(@NotNull NotificationService service,
                                   @NotNull NotificationRenderer<RealtyNotificationPayload> renderer) {
        NotificationCategoryRegistry categories = service.categoryRegistry();
        for (RealtyCategory category : RealtyCategory.values()) {
            String dataType = category.dataType();
            service.registerJsonRenderable(dataType, RealtyNotificationPayload.class, renderer);
            service.dataTypeRegistry().registerDisplayName(dataType, category.label());
            categories.registerCategory(dataType, category.label(), category.description());
            categories.claimDataType(dataType, dataType);
        }
    }

    /**
     * Unregisters <em>every</em> data type. See the class javadoc: doing this partially corrupts the
     * registry for the data types left behind.
     */
    public static void unregisterAll(@NotNull NotificationDataTypeRegistry registry) {
        for (RealtyCategory category : RealtyCategory.values()) {
            registry.unregisterPayloadMapping(category.dataType());
            // Not part of the payload-mapping cascade: a display name is keyed by data type, not by
            // payload class, so dropping the mapping leaves the name behind unless it is said here.
            registry.unregisterDisplayName(category.dataType());
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
    public static void unclaimAll(@NotNull NotificationCategoryRegistry categories) {
        for (RealtyCategory category : RealtyCategory.values()) {
            categories.unclaimDataType(category.dataType(), category.dataType());
        }
    }
}
