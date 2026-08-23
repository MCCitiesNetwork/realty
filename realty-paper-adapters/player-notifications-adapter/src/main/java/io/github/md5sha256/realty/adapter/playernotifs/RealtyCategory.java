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
 * <p>Deliberately free of PlayerNotifications and Bukkit types: the routing decision is the part worth
 * testing, and keeping it free of both means it can be tested without a server or a live PN install.</p>
 */
public enum RealtyCategory {

    AGENT("realty.agent",
            "Realty agents",
            "Agent invitations and removals",
            List.of("notification.agent-invited",
                    "notification.agent-invite-accepted",
                    "notification.agent-invite-rejected",
                    "notification.agent-invite-withdrawn",
                    "notification.agent-removed")),

    AUCTION("realty.auction",
            "Realty auctions",
            "Bids, auction outcomes and bid payment deadlines",
            List.of("notification.outbid",
                    "notification.auction-cancelled",
                    "notification.auction-won",
                    "notification.auction-ended-no-bids",
                    "notification.bid-payment-expired")),

    OFFER("realty.offer",
            "Realty offers",
            "Offers on your regions and offer payment deadlines",
            List.of("notification.offer-placed",
                    "notification.offer-accepted",
                    "notification.offer-rejected",
                    "notification.offer-withdrawn",
                    "notification.offer-payment-expired")),

    LEASE("realty.lease",
            "Realty leases",
            "Rent, lease expiry, terminations and modification proposals",
            List.of("notification.region-rented",
                    "notification.region-unrented",
                    "notification.leasehold-expired",
                    "notification.leasehold-expired-landlord",
                    "notification.modify-proposed-landlord",
                    "notification.modify-proposed-tenant",
                    "notification.modify-accepted",
                    "notification.modify-rejected",
                    "notification.modify-withdrawn",
                    "notification.termination-scheduled-tenant",
                    "notification.termination-scheduled-landlord",
                    "notification.termination-cancelled",
                    "notification.leasehold-terminated-tenant",
                    "notification.leasehold-terminated-landlord")),

    /**
     * Purchases, ownership transfers, and every key no other category claims. Being the fallback is
     * why this constant must exist; see {@link #forMessageKey}.
     */
    GENERAL("realty.general",
            "Realty",
            "Purchases, ownership transfers and anything uncategorised",
            List.of("notification.region-bought",
                    "notification.ownership-transferred"));

    /** Where a key no category claims is routed. */
    public static final RealtyCategory FALLBACK = GENERAL;

    private static final Map<String, RealtyCategory> BY_MESSAGE_KEY = index();

    private final String dataType;
    private final String label;
    private final String description;
    private final List<String> messageKeys;

    RealtyCategory(@NotNull String dataType,
                   @NotNull String label,
                   @NotNull String description,
                   @NotNull List<String> messageKeys) {
        this.dataType = dataType;
        this.label = label;
        this.description = description;
        this.messageKeys = messageKeys;
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
            for (String messageKey : category.messageKeys) {
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
        return this.messageKeys;
    }
}
