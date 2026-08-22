package io.github.md5sha256.realty.adapter.pn;

import io.github.md5sha256.playernotifications.api.NotificationDataTypeRegistry;
import io.github.md5sha256.playernotifications.api.NotificationService;
import io.github.md5sha256.playernotifications.api.category.NotificationCategoryRegistry;
import io.github.md5sha256.playernotifications.api.render.NotificationRenderer;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Registers and unregisters Realty's five PlayerNotifications data types.
 *
 * <p><b>The shutdown footgun.</b> All five data types share one payload class,
 * {@link RealtyNotificationPayload}. {@code NotificationDataTypeRegistry} keys serializers and
 * renderers by payload <em>class</em> but the payload mapping by data type <em>string</em>, so
 * {@code unregisterPayloadMapping("realty.auction")} does not just drop that one mapping — it
 * cascades into {@code unregisterSerializer}/{@code unregisterRenderer} on the shared class,
 * silently leaving the other four data types mapped but with no serializer and no renderer. Every
 * notification they carry then fails at enqueue or render time.</p>
 *
 * <p>Unregistering all five is therefore not a tidiness preference, it is the only correct
 * sequence: partially unregistering is what corrupts the registry. {@link
 * #unregisterAll(NotificationDataTypeRegistry)} exists so no call site can get that wrong, and
 * {@code RegistrationLifecycleTest} asserts the hazard so it stays documented executably. This is
 * a sharp edge in the PlayerNotifications API, not in this module.</p>
 */
public final class RealtyDataTypes {

    /** Category labels, keyed by data type — shown in PN's preference dialogs. */
    private static final Map<String, String> LABELS = Map.of(
            "realty.auction", "Realty auctions",
            "realty.offer", "Realty offers",
            "realty.lease", "Realty leases",
            "realty.agent", "Realty agents",
            "realty.general", "Realty");

    private static final Map<String, String> DESCRIPTIONS = Map.of(
            "realty.auction", "Bids, auction outcomes and bid payment deadlines",
            "realty.offer", "Offers on your regions and offer payment deadlines",
            "realty.lease", "Rent, lease expiry, terminations and modification proposals",
            "realty.agent", "Agent invitations and removals",
            "realty.general", "Purchases, ownership transfers and anything uncategorised");

    private RealtyDataTypes() {
    }

    /**
     * Binds every Realty data type to {@link RealtyNotificationPayload} and claims it under a
     * matching category.
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
        for (String dataType : NotificationCategoryMapper.DATA_TYPES) {
            service.registerJsonRenderable(dataType, RealtyNotificationPayload.class, renderer);
            categories.registerCategory(dataType,
                    LABELS.getOrDefault(dataType, dataType),
                    DESCRIPTIONS.getOrDefault(dataType, ""));
            categories.claimDataType(dataType, dataType);
        }
    }

    /**
     * Unregisters <em>all five</em> data types. See the class javadoc: doing this partially
     * corrupts the registry for the data types left behind.
     */
    public static void unregisterAll(@NotNull NotificationDataTypeRegistry registry) {
        for (String dataType : NotificationCategoryMapper.DATA_TYPES) {
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
    public static void unclaimAll(@NotNull NotificationCategoryRegistry categories) {
        for (String dataType : NotificationCategoryMapper.DATA_TYPES) {
            categories.unclaimDataType(dataType, dataType);
        }
    }
}
