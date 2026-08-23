package io.github.md5sha256.realty.adapter.playernotifs;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Realty's notification categories: the complete set of PlayerNotifications {@code dataType}s this
 * adapter registers, and the {@code messages.yml} keys each one claims.
 *
 * <p><b>Why this is code and not config.</b> Until 1.5.0 the set lived in the module's own
 * {@code categories.yml}. PlayerNotifications now owns category presentation itself: a module
 * registers its categories through {@code NotificationCategoryRegistry}, PN writes them out to its
 * generated {@code categories-defaults.yml}, and the operator reconciles the blocks they care about
 * into PN's {@code categories.yml} — where a label, a description or a regrouping overrides what was
 * registered here. Two config files claiming the same job is one too many, so this side keeps only
 * the part PN cannot know: which Realty message key belongs to which category.</p>
 *
 * <p>The label and description below are therefore <em>defaults</em>, not the last word. They are what
 * an operator sees in {@code categories-defaults.yml} and what the preference dialogs show until they
 * override them.</p>
 *
 * <p><b>Each claimed key also carries a title</b> — the short summary
 * {@link RealtyNotificationRenderer} puts on the notification. Unlike the label and the description
 * this one is not PN's to override and is not a category-level string: PN's inbox lists a row by its
 * rendered <em>title</em> alone and shows the body only once the row is opened, so a category-level
 * title made every row in a category read identically ("Realty leases", fourteen times over) with the
 * message itself invisible from the list.</p>
 *
 * <p>Deliberately free of PlayerNotifications and Bukkit types: the routing decision is the part worth
 * testing, and keeping it free of both means it can be tested without a server or a live PN install.</p>
 */
public enum RealtyCategory {

    AGENT("realty.agent",
            "Realty agents",
            "Agent invitations and removals",
            Map.ofEntries(
                    Map.entry("notification.agent-invited", "Agent invitation"),
                    Map.entry("notification.agent-invite-accepted", "Agent invite accepted"),
                    Map.entry("notification.agent-invite-rejected", "Agent invite rejected"),
                    Map.entry("notification.agent-invite-withdrawn", "Agent invite withdrawn"),
                    Map.entry("notification.agent-removed", "Removed as agent"))),

    AUCTION("realty.auction",
            "Realty auctions",
            "Bids, auction outcomes and bid payment deadlines",
            Map.ofEntries(
                    Map.entry("notification.outbid", "Outbid"),
                    Map.entry("notification.auction-cancelled", "Auction cancelled"),
                    Map.entry("notification.auction-won", "Auction won"),
                    Map.entry("notification.auction-ended-no-bids", "Auction ended without bids"),
                    Map.entry("notification.bid-payment-expired", "Bid payment expired"))),

    OFFER("realty.offer",
            "Realty offers",
            "Offers on your regions and offer payment deadlines",
            Map.ofEntries(
                    Map.entry("notification.offer-placed", "Offer received"),
                    Map.entry("notification.offer-accepted", "Offer accepted"),
                    Map.entry("notification.offer-rejected", "Offer rejected"),
                    Map.entry("notification.offer-withdrawn", "Offer withdrawn"),
                    Map.entry("notification.offer-payment-expired", "Offer payment expired"))),

    LEASE("realty.lease",
            "Realty leases",
            "Rent, lease expiry, terminations and modification proposals",
            Map.ofEntries(
                    Map.entry("notification.region-rented", "Region rented"),
                    Map.entry("notification.region-unrented", "Region unrented"),
                    Map.entry("notification.leasehold-expired", "Lease expired"),
                    Map.entry("notification.leasehold-expired-landlord", "Tenant's lease expired"),
                    Map.entry("notification.modify-proposed-landlord", "New lease terms proposed"),
                    Map.entry("notification.modify-proposed-tenant", "New lease terms requested"),
                    Map.entry("notification.modify-accepted", "Lease terms accepted"),
                    Map.entry("notification.modify-rejected", "Lease terms rejected"),
                    Map.entry("notification.modify-withdrawn", "Lease proposal withdrawn"),
                    Map.entry("notification.termination-scheduled-tenant", "Lease termination scheduled"),
                    Map.entry("notification.termination-scheduled-landlord", "Tenant scheduled termination"),
                    Map.entry("notification.termination-cancelled", "Termination cancelled"),
                    Map.entry("notification.leasehold-terminated-tenant", "Lease ended"),
                    Map.entry("notification.leasehold-terminated-landlord", "Tenant's lease ended"))),

    /**
     * Purchases, ownership transfers, and every key no other category claims. Being the fallback is
     * why this constant must exist; see {@link #forMessageKey}.
     */
    GENERAL("realty.general",
            "Realty",
            "Purchases, ownership transfers and anything uncategorised",
            Map.ofEntries(
                    Map.entry("notification.region-bought", "Region sold"),
                    Map.entry("notification.ownership-transferred", "Ownership transferred")));

    /** Where a key no category claims is routed. */
    public static final RealtyCategory FALLBACK = GENERAL;

    private static final Map<String, RealtyCategory> BY_MESSAGE_KEY = index();

    private final String dataType;
    private final String label;
    private final String description;
    private final Map<String, String> titlesByMessageKey;

    RealtyCategory(@NotNull String dataType,
                   @NotNull String label,
                   @NotNull String description,
                   @NotNull Map<String, String> titlesByMessageKey) {
        this.dataType = dataType;
        this.label = label;
        this.description = description;
        this.titlesByMessageKey = titlesByMessageKey;
    }

    /**
     * Builds the message-key index, failing loudly if two categories claim the same key.
     *
     * <p>A duplicate is a programming error rather than an operator one now, but it is still checked:
     * the failure it would otherwise cause — which category a player must enable to receive the key
     * depending on declaration order — is invisible from both the dialogs and the source.</p>
     */
    private static @NotNull Map<String, RealtyCategory> index() {
        Map<String, RealtyCategory> index = new HashMap<>();
        for (RealtyCategory category : values()) {
            for (String messageKey : category.titlesByMessageKey.keySet()) {
                RealtyCategory claimedBy = index.put(messageKey, category);
                if (claimedBy != null) {
                    throw new IllegalStateException("The message key '" + messageKey
                            + "' is claimed by both " + claimedBy + " and " + category
                            + "; a key may belong to exactly one category");
                }
            }
        }
        return Map.copyOf(index);
    }

    /**
     * The category the given message key belongs to, or {@link #FALLBACK} if no category claims it.
     *
     * <p>An unrecognised key is routed rather than dropped: Realty adds message keys over time and
     * third-party fire sites may use keys of their own, and a notification this enum has never seen is
     * still a notification a player should receive.</p>
     */
    public static @NotNull RealtyCategory forMessageKey(@NotNull String messageKey) {
        Objects.requireNonNull(messageKey, "messageKey");
        return BY_MESSAGE_KEY.getOrDefault(messageKey, FALLBACK);
    }

    /**
     * The short summary the notification renders with, e.g. {@code "Lease expired"}.
     *
     * <p>An unclaimed key has no title of its own and falls back to its category's label — the old
     * behaviour for every key, and still the only honest thing to say about a key no category has
     * been taught about.</p>
     */
    public static @NotNull String titleFor(@NotNull String messageKey) {
        Objects.requireNonNull(messageKey, "messageKey");
        RealtyCategory category = forMessageKey(messageKey);
        return category.titlesByMessageKey.getOrDefault(messageKey, category.label);
    }

    /**
     * Whether any category explicitly claims the given key. Callers use this to log the fallback,
     * because {@link #forMessageKey} cannot distinguish an unclaimed key from one deliberately
     * claimed by {@link #FALLBACK}.
     */
    public static boolean isClaimed(@NotNull String messageKey) {
        return BY_MESSAGE_KEY.containsKey(Objects.requireNonNull(messageKey, "messageKey"));
    }

    /** The PlayerNotifications {@code dataType} this category is registered as. */
    public @NotNull String dataType() {
        return this.dataType;
    }

    /** The default preference-dialog label; PN's {@code categories.yml} may override it. */
    public @NotNull String label() {
        return this.label;
    }

    /** The default preference-dialog description; PN's {@code categories.yml} may override it. */
    public @NotNull String description() {
        return this.description;
    }

    /** The {@code messages.yml} keys this category claims. */
    public @NotNull List<String> messageKeys() {
        return List.copyOf(this.titlesByMessageKey.keySet());
    }

    /** The keys this category claims, mapped to the titles they render with. */
    public @NotNull Map<String, String> titlesByMessageKey() {
        return this.titlesByMessageKey;
    }
}
