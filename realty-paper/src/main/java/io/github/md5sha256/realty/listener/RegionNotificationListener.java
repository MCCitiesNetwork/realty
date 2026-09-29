package io.github.md5sha256.realty.listener;

import io.github.md5sha256.realty.api.CurrencyFormatter;
import io.github.md5sha256.realty.api.ExecutorState;
import io.github.md5sha256.realty.api.DateTimeFormatters;
import io.github.md5sha256.realty.api.LeaseholdRoles;
import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.api.WorldGuardRegion;
import io.github.md5sha256.realty.api.event.LeaseExpiredEvent;
import io.github.md5sha256.realty.api.event.LeaseModificationProposedEvent;
import io.github.md5sha256.realty.api.event.LeaseModificationResolvedEvent;
import io.github.md5sha256.realty.api.event.LeaseTerminatedEvent;
import io.github.md5sha256.realty.api.event.LeaseTerminationCancelledEvent;
import io.github.md5sha256.realty.api.event.LeaseTerminationScheduledEvent;
import io.github.md5sha256.realty.api.event.RealtyNotificationEvent;
import io.github.md5sha256.realty.api.event.RegionBoughtEvent;
import io.github.md5sha256.realty.api.event.RegionRentedEvent;
import io.github.md5sha256.realty.api.event.RegionUnrentedEvent;
import io.github.md5sha256.realty.event.RealtyEventDispatch;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.localisation.MessageKeys;
import io.github.md5sha256.realty.notify.PartyRecipients;
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
    private final Logger logger;

    public RegionNotificationListener(@NotNull RealtyEventDispatch events,
                                      @NotNull MessageContainer messages,
                                      @NotNull PartyNames partyNames,
                                      @NotNull PartyRecipients recipients,
                                      @NotNull ExecutorState executorState,
                                      @NotNull Logger logger) {
        this.events = events;
        this.messages = messages;
        this.partyNames = partyNames;
        this.recipients = recipients;
        this.executorState = executorState;
        this.logger = logger;
    }

    @EventHandler
    public void onRegionBought(@NotNull RegionBoughtEvent event) {
        UUID seller = event.getPreviousTitleHolderId();
        if (seller == null) {
            return;
        }
        notifyPlayer(seller, event.getRegion(), MessageKeys.NOTIFICATION_REGION_BOUGHT,
                Placeholder.unparsed("player", partyNames.display(event.getBuyerId())),
                Placeholder.unparsed("price", CurrencyFormatter.format(event.getPrice())),
                Placeholder.unparsed("region", event.getRegionId()));
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

    private void notifyPlayer(@NotNull UUID playerId, @NotNull WorldGuardRegion region, @NotNull String key,
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
    private void notifyParty(@Nullable Party party, @NotNull WorldGuardRegion region, @NotNull String key,
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
