package io.github.md5sha256.realty.listener;

import io.github.md5sha256.realty.api.CurrencyFormatter;
import io.github.md5sha256.realty.api.Money;
import io.github.md5sha256.realty.api.ExecutorState;
import io.github.md5sha256.realty.api.DateTimeFormatters;
import io.github.md5sha256.realty.api.LeaseholdRoles;
import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.api.WorldGuardRegion;
import io.github.md5sha256.realty.api.event.AuctionBidPlacedEvent;
import io.github.md5sha256.realty.api.event.AuctionCancelledEvent;
import io.github.md5sha256.realty.api.event.AuctionPaymentExpiredEvent;
import io.github.md5sha256.realty.api.event.AuctionWonPurchaseEvent;
import io.github.md5sha256.realty.api.event.FeePaidEvent;
import io.github.md5sha256.realty.api.event.LeaseExpiredEvent;
import io.github.md5sha256.realty.api.event.LeaseModificationProposedEvent;
import io.github.md5sha256.realty.api.event.LeaseModificationResolvedEvent;
import io.github.md5sha256.realty.api.event.LeaseTerminatedEvent;
import io.github.md5sha256.realty.api.event.LeaseTerminationCancelledEvent;
import io.github.md5sha256.realty.api.event.LeaseTerminationScheduledEvent;
import io.github.md5sha256.realty.api.event.OfferAcceptedEvent;
import io.github.md5sha256.realty.api.event.OfferPaymentExpiredEvent;
import io.github.md5sha256.realty.api.event.OfferPlacedEvent;
import io.github.md5sha256.realty.api.event.OfferPurchaseCompletedEvent;
import io.github.md5sha256.realty.api.event.OfferRejectedEvent;
import io.github.md5sha256.realty.api.event.OfferWithdrawnEvent;
import io.github.md5sha256.realty.api.event.PaymentExpiredEvent;
import io.github.md5sha256.realty.api.event.RefundPaidEvent;
import io.github.md5sha256.realty.api.event.SaleProceedsPaidEvent;
import io.github.md5sha256.realty.api.event.RealtyNotificationEvent;
import io.github.md5sha256.realty.api.event.RegionBoughtEvent;
import io.github.md5sha256.realty.api.event.RegionRentedEvent;
import io.github.md5sha256.realty.api.event.RegionUnrentedEvent;
import io.github.md5sha256.realty.command.util.Deadlines;
import io.github.md5sha256.realty.event.RealtyEventDispatch;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.localisation.MessageKeys;
import io.github.md5sha256.realty.notify.PartyRecipients;
import io.github.md5sha256.realty.settings.Settings;
import io.github.md5sha256.realty.util.PartyNames;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Delivers counterparty notifications in response to Realty's own post-commit
 * lifecycle events. This decouples notification delivery from the command and
 * scheduler code that triggers the underlying actions: the actor's own command
 * feedback stays in the command, while the "your region was rented / bought /
 * unrented / expired" notices for the other party are centralised here and
 * driven entirely by events.
 */
public final class RegionNotificationListener implements Listener {

    private final RealtyEventDispatch events;
    private final MessageContainer messages;
    private final PartyNames partyNames;
    private final PartyRecipients recipients;
    private final ExecutorState executorState;
    private final AtomicReference<Settings> settings;
    private final Logger logger;

    /** @param settings read for the date format of a payment deadline */
    public RegionNotificationListener(@NotNull RealtyEventDispatch events,
                                      @NotNull MessageContainer messages,
                                      @NotNull PartyNames partyNames,
                                      @NotNull PartyRecipients recipients,
                                      @NotNull ExecutorState executorState,
                                      @NotNull AtomicReference<Settings> settings,
                                      @NotNull Logger logger) {
        this.events = events;
        this.messages = messages;
        this.partyNames = partyNames;
        this.recipients = recipients;
        this.executorState = executorState;
        this.settings = settings;
        this.logger = logger;
    }

    @EventHandler
    public void onRegionBought(@NotNull RegionBoughtEvent event) {
        Party.playerUuidOf(event.getPreviousTitleHolder()).ifPresent(seller ->
                notifyPlayer(seller, event.getRegion(), MessageKeys.NOTIFICATION_REGION_BOUGHT,
                        Placeholder.unparsed("player", partyNames.display(event.getNewTitleHolder())),
                        Placeholder.unparsed("price", CurrencyFormatter.format(event.getPrice())),
                        Placeholder.unparsed("region", event.getRegionId())));
    }

    @EventHandler
    public void onAuctionWonPurchase(@NotNull AuctionWonPurchaseEvent event) {
        Party.playerUuidOf(event.getPreviousTitleHolder()).ifPresent(seller ->
                notifyPlayer(seller, event.getRegion(), MessageKeys.NOTIFICATION_OWNERSHIP_TRANSFERRED,
                        Placeholder.unparsed("player", partyNames.display(event.getNewTitleHolder())),
                        Placeholder.unparsed("region", event.getRegionId())));
    }

    /**
     * Tells the seller, once the money arrives, what the region sold for and what the sale left
     * them after the fees they pay. The seller may be an authority, told through the people who act
     * for it. Proceeds of nothing, when the seller's fees took the whole price, are not told.
     */
    @EventHandler
    public void onSaleProceedsPaid(@NotNull SaleProceedsPaidEvent event) {
        if (Money.cents(event.getAmount()) <= 0) {
            return;
        }
        boolean sellerPaidFees = !Money.atLeast(event.getAmount(), event.getPrice());
        notifyParty(event.getSeller(), null,
                sellerPaidFees ? MessageKeys.NOTIFICATION_SALE_PROCEEDS : MessageKeys.NOTIFICATION_SALE_PROCEEDS_NO_FEES,
                Placeholder.unparsed("region", event.getRegionId()),
                Placeholder.unparsed("price", CurrencyFormatter.format(event.getPrice())),
                Placeholder.unparsed("received", CurrencyFormatter.format(event.getAmount())));
    }

    /**
     * Tells the buyer a refund that failed before has now been paid. A refund paid on the first
     * attempt is told with the expiry that caused it, so it is not told again here.
     */
    @EventHandler
    public void onRefundPaid(@NotNull RefundPaidEvent event) {
        if (!event.isRetry()) {
            return;
        }
        notifyPlayer(event.getBuyerId(), null, MessageKeys.NOTIFICATION_REFUND_PAID,
                Placeholder.unparsed("region", event.getRegionId()),
                Placeholder.unparsed("amount", CurrencyFormatter.format(event.getAmount())));
    }

    /** Tells a fee's recipient, once it is paid out, what they were paid and for which sale. */
    @EventHandler
    public void onFeePaid(@NotNull FeePaidEvent event) {
        notifyParty(event.getRecipient(), null, MessageKeys.NOTIFICATION_SALE_PAYOUT,
                Placeholder.unparsed("amount", CurrencyFormatter.format(event.getAmount())),
                Placeholder.unparsed("source", event.getSource()),
                Placeholder.unparsed("region", event.getRegionId()));
    }

    /**
     * The notices for a missed payment deadline, one set for bids and one for offers, so a
     * notification module can file each with its kind of sale.
     *
     * @param refunded      the refund was paid
     * @param refundPending the refund failed and is pending
     * @param nothingPaid   the buyer had paid nothing
     * @param oldRules      the payment began before sale fees, so its money went to the seller and
     *                      nothing is refunded
     */
    record ExpiryKeys(@NotNull String refunded, @NotNull String refundPending,
                      @NotNull String nothingPaid, @NotNull String oldRules) {}

    static final ExpiryKeys BID_EXPIRY_KEYS = new ExpiryKeys(
            MessageKeys.NOTIFICATION_BID_PAYMENT_EXPIRED_REFUNDED,
            MessageKeys.NOTIFICATION_BID_REFUND_PENDING,
            MessageKeys.NOTIFICATION_BID_PAYMENT_EXPIRED_NOTHING_PAID,
            MessageKeys.NOTIFICATION_BID_PAYMENT_EXPIRED_OLD_RULES);

    static final ExpiryKeys OFFER_EXPIRY_KEYS = new ExpiryKeys(
            MessageKeys.NOTIFICATION_OFFER_PAYMENT_EXPIRED_REFUNDED,
            MessageKeys.NOTIFICATION_OFFER_REFUND_PENDING,
            MessageKeys.NOTIFICATION_OFFER_PAYMENT_EXPIRED_NOTHING_PAID,
            MessageKeys.NOTIFICATION_OFFER_PAYMENT_EXPIRED_OLD_RULES);

    /** The key of the notice for a missed payment deadline, from {@code keys}. */
    static @NotNull String expiryKey(@NotNull PaymentExpiredEvent event, @NotNull ExpiryKeys keys) {
        if (event.isRefundPaid()) {
            return keys.refunded();
        }
        if (event.getRefundAmount() > 0) {
            return keys.refundPending();
        }
        return event.isOldRules() ? keys.oldRules() : keys.nothingPaid();
    }

    @EventHandler
    public void onAuctionBidPlaced(@NotNull AuctionBidPlacedEvent event) {
        event.getPreviousBidderId().ifPresent(outbid ->
                notifyPlayer(outbid, event.getRegion(), MessageKeys.NOTIFICATION_OUTBID,
                        Placeholder.unparsed("region", event.getRegionId()),
                        Placeholder.unparsed("amount", CurrencyFormatter.format(event.getBidAmount()))));
    }

    @EventHandler
    public void onAuctionCancelled(@NotNull AuctionCancelledEvent event) {
        for (UUID bidder : event.getBidderIds()) {
            notifyPlayer(bidder, event.getRegion(), MessageKeys.NOTIFICATION_AUCTION_CANCELLED,
                    Placeholder.unparsed("region", event.getRegionId()));
        }
    }

    @EventHandler
    public void onAuctionPaymentExpired(@NotNull AuctionPaymentExpiredEvent event) {
        notifyPlayer(event.getBidderId(), event.getRegion().orElse(null),
                expiryKey(event, BID_EXPIRY_KEYS),
                Placeholder.unparsed("region", event.getRegionId()),
                Placeholder.unparsed("amount", CurrencyFormatter.format(event.getRefundAmount())));
    }

    @EventHandler
    public void onOfferPaymentExpired(@NotNull OfferPaymentExpiredEvent event) {
        notifyPlayer(event.getOffererId(), event.getRegion().orElse(null),
                expiryKey(event, OFFER_EXPIRY_KEYS),
                Placeholder.unparsed("region", event.getRegionId()),
                Placeholder.unparsed("amount", CurrencyFormatter.format(event.getRefundAmount())));
    }

    @EventHandler
    public void onOfferPlaced(@NotNull OfferPlacedEvent event) {
        notifyParty(event.getTitleHolder(), event.getRegion(), MessageKeys.NOTIFICATION_OFFER_PLACED,
                Placeholder.unparsed("player", partyNames.display(event.getOffererId())),
                Placeholder.unparsed("price", CurrencyFormatter.format(event.getPrice())),
                Placeholder.unparsed("region", event.getRegionId()));
    }

    @EventHandler
    public void onOfferWithdrawn(@NotNull OfferWithdrawnEvent event) {
        notifyParty(event.getTitleHolder(), event.getRegion(), MessageKeys.NOTIFICATION_OFFER_WITHDRAWN,
                Placeholder.unparsed("player", partyNames.display(event.getOffererId())),
                Placeholder.unparsed("region", event.getRegionId()));
    }

    /** Tells the offerer their offer was accepted, and what they owe, fees included, by when. */
    @EventHandler
    public void onOfferAccepted(@NotNull OfferAcceptedEvent event) {
        notifyPlayer(event.getOffererId(), event.getRegion(), MessageKeys.NOTIFICATION_OFFER_ACCEPTED,
                Placeholder.unparsed("region", event.getRegionId()),
                Placeholder.unparsed("total", CurrencyFormatter.format(event.getTotalDue())),
                Placeholder.unparsed("price", CurrencyFormatter.format(event.getPrice())),
                Deadlines.placeholder(this.messages, this.settings.get().dateFormat(),
                        event.getPaymentDeadline().orElse(null)));
    }

    @EventHandler
    public void onOfferRejected(@NotNull OfferRejectedEvent event) {
        notifyPlayer(event.getOffererId(), event.getRegion(), MessageKeys.NOTIFICATION_OFFER_REJECTED,
                Placeholder.unparsed("region", event.getRegionId()));
    }

    @EventHandler
    public void onOfferPurchaseCompleted(@NotNull OfferPurchaseCompletedEvent event) {
        Party.playerUuidOf(event.getPreviousTitleHolder()).ifPresent(seller ->
                notifyPlayer(seller, event.getRegion(), MessageKeys.NOTIFICATION_OWNERSHIP_TRANSFERRED,
                        Placeholder.unparsed("player", partyNames.display(event.getNewTitleHolder())),
                        Placeholder.unparsed("region", event.getRegionId())));
    }

    @EventHandler
    public void onRegionRented(@NotNull RegionRentedEvent event) {
        notifyParty(event.getLandlord(), event.getRegion(), MessageKeys.NOTIFICATION_REGION_RENTED,
                Placeholder.unparsed("player", partyNames.display(event.getTenant())),
                Placeholder.unparsed("price", CurrencyFormatter.format(event.getPrice())),
                Placeholder.unparsed("region", event.getRegionId()));
    }

    @EventHandler
    public void onRegionUnrented(@NotNull RegionUnrentedEvent event) {
        notifyParty(event.getLandlord(), event.getRegion(), MessageKeys.NOTIFICATION_REGION_UNRENTED,
                Placeholder.unparsed("player", partyNames.display(event.getTenant())),
                Placeholder.unparsed("region", event.getRegionId()),
                Placeholder.unparsed("refund", CurrencyFormatter.format(event.getRefund())));
    }

    @EventHandler
    public void onLeaseExpired(@NotNull LeaseExpiredEvent event) {
        notifyParty(event.getTenant(), event.getRegion(), MessageKeys.NOTIFICATION_LEASEHOLD_EXPIRED,
                Placeholder.unparsed("region", event.getRegionId()));
        notifyParty(event.getLandlord(), event.getRegion(), MessageKeys.NOTIFICATION_LEASEHOLD_EXPIRED_LANDLORD,
                Placeholder.unparsed("region", event.getRegionId()));
    }

    @EventHandler
    public void onModificationProposed(@NotNull LeaseModificationProposedEvent event) {
        if (LeaseholdRoles.LANDLORD.equals(event.getProposerRole())) {
            // Landlord proposed: notify the tenant, who decides by renewing or not.
            notifyParty(event.getTenant(), event.getRegion(), MessageKeys.NOTIFICATION_MODIFY_PROPOSED_LANDLORD,
                    Placeholder.unparsed("region", event.getRegionId()));
        } else {
            // Tenant proposed: notify the landlord, who must accept or reject.
            notifyParty(event.getLandlord(), event.getRegion(), MessageKeys.NOTIFICATION_MODIFY_PROPOSED_TENANT,
                    Placeholder.unparsed("player", partyNames.display(event.getProposerId())),
                    Placeholder.unparsed("region", event.getRegionId()));
        }
    }

    @EventHandler
    public void onModificationResolved(@NotNull LeaseModificationResolvedEvent event) {
        Party tenant = event.getTenant();
        switch (event.getResolution()) {
            case "ACCEPTED" -> notifyParty(tenant, event.getRegion(), MessageKeys.NOTIFICATION_MODIFY_ACCEPTED,
                    Placeholder.unparsed("region", event.getRegionId()));
            case "REJECTED" -> notifyParty(tenant, event.getRegion(), MessageKeys.NOTIFICATION_MODIFY_REJECTED,
                    Placeholder.unparsed("region", event.getRegionId()));
            // Notify the party that did not withdraw.
            case "WITHDRAWN" -> notifyParty(
                    LeaseholdRoles.LANDLORD.equals(event.getProposerRole()) ? tenant : event.getLandlord(),
                    event.getRegion(), MessageKeys.NOTIFICATION_MODIFY_WITHDRAWN,
                    Placeholder.unparsed("region", event.getRegionId()));
            default -> { }
        }
    }

    @EventHandler
    public void onTerminationScheduled(@NotNull LeaseTerminationScheduledEvent event) {
        String date = event.getEffectiveDate().format(DateTimeFormatters.DATE_TIME);
        if (LeaseholdRoles.LANDLORD.equals(event.getTerminatedByRole())) {
            notifyParty(event.getTenant(), event.getRegion(), MessageKeys.NOTIFICATION_TERMINATION_SCHEDULED_TENANT,
                    Placeholder.unparsed("region", event.getRegionId()),
                    Placeholder.unparsed("date", date));
        } else {
            notifyParty(event.getLandlord(), event.getRegion(), MessageKeys.NOTIFICATION_TERMINATION_SCHEDULED_LANDLORD,
                    Placeholder.unparsed("region", event.getRegionId()),
                    Placeholder.unparsed("date", date));
        }
    }

    @EventHandler
    public void onTerminationCancelled(@NotNull LeaseTerminationCancelledEvent event) {
        // Notify the party that did not initiate the (now-cancelled) termination.
        Party other = LeaseholdRoles.LANDLORD.equals(event.getTerminatedByRole())
                ? event.getTenant() : event.getLandlord();
        notifyParty(other, event.getRegion(), MessageKeys.NOTIFICATION_TERMINATION_CANCELLED,
                Placeholder.unparsed("region", event.getRegionId()));
    }

    @EventHandler
    public void onLeaseTerminated(@NotNull LeaseTerminatedEvent event) {
        notifyParty(event.getTenant(), event.getRegion(), MessageKeys.NOTIFICATION_LEASEHOLD_TERMINATED_TENANT,
                Placeholder.unparsed("region", event.getRegionId()),
                Placeholder.unparsed("refund", CurrencyFormatter.format(event.getRefund())));
        notifyParty(event.getLandlord(), event.getRegion(), MessageKeys.NOTIFICATION_LEASEHOLD_TERMINATED_LANDLORD,
                Placeholder.unparsed("region", event.getRegionId()));
    }

    private void notifyPlayer(@NotNull UUID playerId, @Nullable WorldGuardRegion region, @NotNull String key,
                              @NotNull TagResolver... placeholders) {
        this.events.fireSync(new RealtyNotificationEvent(List.of(playerId), key,
                this.messages.messageFor(key, placeholders), region));
    }

    /**
     * Tells the people who act for {@code party}. A player is told directly; an account or a group
     * has to be expanded into its people, which does blocking I/O, so that happens on the database
     * executor. The message is rendered here, on the calling thread. Nobody is told when {@code party}
     * is missing or expands to no one, and a failure to expand is logged and never thrown, so that a
     * notice can never break the action that caused it.
     */
    private void notifyParty(@Nullable Party party, @Nullable WorldGuardRegion region, @NotNull String key,
                             @NotNull TagResolver... placeholders) {
        if (party == null) {
            return;
        }
        Optional<UUID> playerId = Party.playerUuidOf(party);
        if (playerId.isPresent()) {
            notifyPlayer(playerId.get(), region, key, placeholders);
            return;
        }
        Component message = this.messages.messageFor(key, placeholders);
        this.executorState.dbExec().execute(() -> {
            try {
                List<UUID> people = this.recipients.expand(party);
                if (!people.isEmpty()) {
                    this.events.fireSync(new RealtyNotificationEvent(people, key, message, region));
                }
            } catch (RuntimeException ex) {
                this.logger.log(Level.WARNING, "Could not notify " + party + " about " + key, ex);
            }
        });
    }
}
