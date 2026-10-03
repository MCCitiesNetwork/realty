package io.github.md5sha256.realty.listener;

import io.github.md5sha256.realty.api.CurrencyFormatter;
import io.github.md5sha256.realty.api.DateTimeFormatters;
import io.github.md5sha256.realty.api.LeaseholdRoles;
import io.github.md5sha256.realty.api.Party;
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
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

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

    public RegionNotificationListener(@NotNull RealtyEventDispatch events,
                                      @NotNull MessageContainer messages) {
        this.events = events;
        this.messages = messages;
    }

    @EventHandler
    public void onRegionBought(@NotNull RegionBoughtEvent event) {
        UUID seller = event.getPreviousTitleHolderId();
        if (seller == null) {
            return;
        }
        this.events.fireSync(new RealtyNotificationEvent(List.of(seller),
                MessageKeys.NOTIFICATION_REGION_BOUGHT,
                this.messages.messageFor(MessageKeys.NOTIFICATION_REGION_BOUGHT,
                        Placeholder.unparsed("player", resolveName(event.getBuyerId())),
                        Placeholder.unparsed("price", CurrencyFormatter.format(event.getPrice())),
                        Placeholder.unparsed("region", event.getRegionId())),
                event.getRegion()));
    }

    @EventHandler
    public void onRegionRented(@NotNull RegionRentedEvent event) {
        List<UUID> recipients = recipientsOf(event.getLandlord());
        if (recipients.isEmpty()) {
            return;
        }
        this.events.fireSync(new RealtyNotificationEvent(recipients,
                MessageKeys.NOTIFICATION_REGION_RENTED,
                this.messages.messageFor(MessageKeys.NOTIFICATION_REGION_RENTED,
                        Placeholder.unparsed("player", resolveName(event.getTenant())),
                        Placeholder.unparsed("price", CurrencyFormatter.format(event.getPrice())),
                        Placeholder.unparsed("region", event.getRegionId())),
                event.getRegion()));
    }

    @EventHandler
    public void onRegionUnrented(@NotNull RegionUnrentedEvent event) {
        List<UUID> recipients = recipientsOf(event.getLandlord());
        if (recipients.isEmpty()) {
            return;
        }
        this.events.fireSync(new RealtyNotificationEvent(recipients,
                MessageKeys.NOTIFICATION_REGION_UNRENTED,
                this.messages.messageFor(MessageKeys.NOTIFICATION_REGION_UNRENTED,
                        Placeholder.unparsed("player", resolveName(event.getTenant())),
                        Placeholder.unparsed("region", event.getRegionId()),
                        Placeholder.unparsed("refund", CurrencyFormatter.format(event.getRefund()))),
                event.getRegion()));
    }

    @EventHandler
    public void onLeaseExpired(@NotNull LeaseExpiredEvent event) {
        this.events.fireSync(new RealtyNotificationEvent(recipientsOf(event.getTenant()),
                MessageKeys.NOTIFICATION_LEASEHOLD_EXPIRED,
                this.messages.messageFor(MessageKeys.NOTIFICATION_LEASEHOLD_EXPIRED,
                        Placeholder.unparsed("region", event.getRegionId())),
                event.getRegion()));
        List<UUID> landlordRecipients = recipientsOf(event.getLandlord());
        if (!landlordRecipients.isEmpty()) {
            this.events.fireSync(new RealtyNotificationEvent(landlordRecipients,
                    MessageKeys.NOTIFICATION_LEASEHOLD_EXPIRED_LANDLORD,
                    this.messages.messageFor(MessageKeys.NOTIFICATION_LEASEHOLD_EXPIRED_LANDLORD,
                            Placeholder.unparsed("region", event.getRegionId())),
                    event.getRegion()));
        }
    }

    @EventHandler
    public void onModificationProposed(@NotNull LeaseModificationProposedEvent event) {
        if (LeaseholdRoles.LANDLORD.equals(event.getProposerRole())) {
            // Landlord proposed: notify the tenant, who decides by renewing or not.
            this.events.fireSync(new RealtyNotificationEvent(recipientsOf(event.getTenant()),
                    MessageKeys.NOTIFICATION_MODIFY_PROPOSED_LANDLORD,
                    this.messages.messageFor(MessageKeys.NOTIFICATION_MODIFY_PROPOSED_LANDLORD,
                            Placeholder.unparsed("region", event.getRegionId())),
                    event.getRegion()));
        } else {
            // Tenant proposed: notify the landlord, who must accept or reject.
            List<UUID> recipients = recipientsOf(event.getLandlord());
            if (recipients.isEmpty()) {
                return;
            }
            this.events.fireSync(new RealtyNotificationEvent(recipients,
                    MessageKeys.NOTIFICATION_MODIFY_PROPOSED_TENANT,
                    this.messages.messageFor(MessageKeys.NOTIFICATION_MODIFY_PROPOSED_TENANT,
                            Placeholder.unparsed("player", resolveName(event.getProposerId())),
                            Placeholder.unparsed("region", event.getRegionId())),
                    event.getRegion()));
        }
    }

    @EventHandler
    public void onModificationResolved(@NotNull LeaseModificationResolvedEvent event) {
        switch (event.getResolution()) {
            case "ACCEPTED" -> this.events.fireSync(new RealtyNotificationEvent(recipientsOf(event.getTenant()),
                    MessageKeys.NOTIFICATION_MODIFY_ACCEPTED,
                    this.messages.messageFor(MessageKeys.NOTIFICATION_MODIFY_ACCEPTED,
                            Placeholder.unparsed("region", event.getRegionId())),
                    event.getRegion()));
            case "REJECTED" -> this.events.fireSync(new RealtyNotificationEvent(recipientsOf(event.getTenant()),
                    MessageKeys.NOTIFICATION_MODIFY_REJECTED,
                    this.messages.messageFor(MessageKeys.NOTIFICATION_MODIFY_REJECTED,
                            Placeholder.unparsed("region", event.getRegionId())),
                    event.getRegion()));
            case "WITHDRAWN" -> {
                // Notify the party that did not withdraw.
                List<UUID> recipients = LeaseholdRoles.LANDLORD.equals(event.getProposerRole())
                        ? recipientsOf(event.getTenant()) : recipientsOf(event.getLandlord());
                if (!recipients.isEmpty()) {
                    this.events.fireSync(new RealtyNotificationEvent(recipients,
                            MessageKeys.NOTIFICATION_MODIFY_WITHDRAWN,
                            this.messages.messageFor(MessageKeys.NOTIFICATION_MODIFY_WITHDRAWN,
                                    Placeholder.unparsed("region", event.getRegionId())),
                            event.getRegion()));
                }
            }
            default -> { }
        }
    }

    @EventHandler
    public void onTerminationScheduled(@NotNull LeaseTerminationScheduledEvent event) {
        String date = event.getEffectiveDate().format(DateTimeFormatters.DATE_TIME);
        if (LeaseholdRoles.LANDLORD.equals(event.getTerminatedByRole())) {
            this.events.fireSync(new RealtyNotificationEvent(recipientsOf(event.getTenant()),
                    MessageKeys.NOTIFICATION_TERMINATION_SCHEDULED_TENANT,
                    this.messages.messageFor(MessageKeys.NOTIFICATION_TERMINATION_SCHEDULED_TENANT,
                            Placeholder.unparsed("region", event.getRegionId()),
                            Placeholder.unparsed("date", date)),
                    event.getRegion()));
        } else {
            List<UUID> recipients = recipientsOf(event.getLandlord());
            if (recipients.isEmpty()) {
                return;
            }
            this.events.fireSync(new RealtyNotificationEvent(recipients,
                    MessageKeys.NOTIFICATION_TERMINATION_SCHEDULED_LANDLORD,
                    this.messages.messageFor(MessageKeys.NOTIFICATION_TERMINATION_SCHEDULED_LANDLORD,
                            Placeholder.unparsed("region", event.getRegionId()),
                            Placeholder.unparsed("date", date)),
                    event.getRegion()));
        }
    }

    @EventHandler
    public void onTerminationCancelled(@NotNull LeaseTerminationCancelledEvent event) {
        // Notify the party that did not initiate the (now-cancelled) termination.
        List<UUID> recipients = LeaseholdRoles.LANDLORD.equals(event.getTerminatedByRole())
                ? recipientsOf(event.getTenant()) : recipientsOf(event.getLandlord());
        if (recipients.isEmpty()) {
            return;
        }
        this.events.fireSync(new RealtyNotificationEvent(recipients,
                MessageKeys.NOTIFICATION_TERMINATION_CANCELLED,
                this.messages.messageFor(MessageKeys.NOTIFICATION_TERMINATION_CANCELLED,
                        Placeholder.unparsed("region", event.getRegionId())),
                event.getRegion()));
    }

    @EventHandler
    public void onLeaseTerminated(@NotNull LeaseTerminatedEvent event) {
        this.events.fireSync(new RealtyNotificationEvent(recipientsOf(event.getTenant()),
                MessageKeys.NOTIFICATION_LEASEHOLD_TERMINATED_TENANT,
                this.messages.messageFor(MessageKeys.NOTIFICATION_LEASEHOLD_TERMINATED_TENANT,
                        Placeholder.unparsed("region", event.getRegionId()),
                        Placeholder.unparsed("refund", CurrencyFormatter.format(event.getRefund()))),
                event.getRegion()));
        List<UUID> landlordRecipients = recipientsOf(event.getLandlord());
        if (!landlordRecipients.isEmpty()) {
            this.events.fireSync(new RealtyNotificationEvent(landlordRecipients,
                    MessageKeys.NOTIFICATION_LEASEHOLD_TERMINATED_LANDLORD,
                    this.messages.messageFor(MessageKeys.NOTIFICATION_LEASEHOLD_TERMINATED_LANDLORD,
                            Placeholder.unparsed("region", event.getRegionId())),
                    event.getRegion()));
        }
    }

    /**
     * The players to notify on behalf of {@code party}: the player themselves for a
     * {@link Party.Personal}, or nobody for any other kind of party. A later task expands
     * a non-player party into its members; until then, a notification addressed to one is
     * simply not sent, rather than address an empty list or throw.
     */
    private static @NotNull List<UUID> recipientsOf(@NotNull Party party) {
        return Party.playerUuidOf(party).map(playerId -> List.of(playerId)).orElse(List.of());
    }

    /**
     * Resolves a player's display name for use in notification text, falling
     * back to the online player and finally the raw UUID when no name is known.
     */
    private @NotNull String resolveName(@NotNull UUID playerId) {
        Player online = Bukkit.getPlayer(playerId);
        if (online != null) {
            return online.getName();
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(playerId);
        String name = offline.getName();
        return name != null ? name : playerId.toString();
    }

    /** Interim: a player by name, any other party by its record form. */
    private @NotNull String resolveName(@NotNull Party party) {
        return Party.playerUuidOf(party).map(playerId -> resolveName(playerId)).orElse(party.toString());
    }
}
