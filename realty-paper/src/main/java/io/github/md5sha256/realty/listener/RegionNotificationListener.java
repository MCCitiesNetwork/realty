package io.github.md5sha256.realty.listener;

import io.github.md5sha256.realty.api.CurrencyFormatter;
import io.github.md5sha256.realty.api.DateTimeFormatters;
import io.github.md5sha256.realty.api.LeaseholdRoles;
import io.github.md5sha256.realty.api.NotificationService;
import io.github.md5sha256.realty.api.event.LeaseExpiredEvent;
import io.github.md5sha256.realty.api.event.LeaseModificationProposedEvent;
import io.github.md5sha256.realty.api.event.LeaseModificationResolvedEvent;
import io.github.md5sha256.realty.api.event.LeaseTerminatedEvent;
import io.github.md5sha256.realty.api.event.LeaseTerminationCancelledEvent;
import io.github.md5sha256.realty.api.event.LeaseTerminationScheduledEvent;
import io.github.md5sha256.realty.api.event.RegionBoughtEvent;
import io.github.md5sha256.realty.api.event.RegionRentedEvent;
import io.github.md5sha256.realty.api.event.RegionUnrentedEvent;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.localisation.MessageKeys;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

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

    private final NotificationService notificationService;
    private final MessageContainer messages;

    public RegionNotificationListener(@NotNull NotificationService notificationService,
                                      @NotNull MessageContainer messages) {
        this.notificationService = notificationService;
        this.messages = messages;
    }

    @EventHandler
    public void onRegionBought(@NotNull RegionBoughtEvent event) {
        UUID seller = event.getPreviousTitleHolderId();
        if (seller == null) {
            return;
        }
        this.notificationService.queueNotification(seller,
                this.messages.messageFor(MessageKeys.NOTIFICATION_REGION_BOUGHT,
                        Placeholder.unparsed("player", resolveName(event.getBuyerId())),
                        Placeholder.unparsed("price", CurrencyFormatter.format(event.getPrice())),
                        Placeholder.unparsed("region", event.getRegionId())));
    }

    @EventHandler
    public void onRegionRented(@NotNull RegionRentedEvent event) {
        this.notificationService.queueNotification(event.getLandlordId(),
                this.messages.messageFor(MessageKeys.NOTIFICATION_REGION_RENTED,
                        Placeholder.unparsed("player", resolveName(event.getTenantId())),
                        Placeholder.unparsed("price", CurrencyFormatter.format(event.getPrice())),
                        Placeholder.unparsed("region", event.getRegionId())));
    }

    @EventHandler
    public void onRegionUnrented(@NotNull RegionUnrentedEvent event) {
        this.notificationService.queueNotification(event.getLandlordId(),
                this.messages.messageFor(MessageKeys.NOTIFICATION_REGION_UNRENTED,
                        Placeholder.unparsed("player", resolveName(event.getTenantId())),
                        Placeholder.unparsed("region", event.getRegionId()),
                        Placeholder.unparsed("refund", CurrencyFormatter.format(event.getRefund()))));
    }

    @EventHandler
    public void onLeaseExpired(@NotNull LeaseExpiredEvent event) {
        this.notificationService.queueNotification(event.getTenantId(),
                this.messages.messageFor(MessageKeys.NOTIFICATION_LEASEHOLD_EXPIRED,
                        Placeholder.unparsed("region", event.getRegionId())));
        this.notificationService.queueNotification(event.getLandlordId(),
                this.messages.messageFor(MessageKeys.NOTIFICATION_LEASEHOLD_EXPIRED_LANDLORD,
                        Placeholder.unparsed("region", event.getRegionId())));
    }

    @EventHandler
    public void onModificationProposed(@NotNull LeaseModificationProposedEvent event) {
        if (LeaseholdRoles.LANDLORD.equals(event.getProposerRole())) {
            // Landlord proposed: notify the tenant, who decides by renewing or not.
            this.notificationService.queueNotification(event.getTenantId(),
                    this.messages.messageFor(MessageKeys.NOTIFICATION_MODIFY_PROPOSED_LANDLORD,
                            Placeholder.unparsed("region", event.getRegionId())));
        } else {
            // Tenant proposed: notify the landlord, who must accept or reject.
            this.notificationService.queueNotification(event.getLandlordId(),
                    this.messages.messageFor(MessageKeys.NOTIFICATION_MODIFY_PROPOSED_TENANT,
                            Placeholder.unparsed("player", resolveName(event.getProposerId())),
                            Placeholder.unparsed("region", event.getRegionId())));
        }
    }

    @EventHandler
    public void onModificationResolved(@NotNull LeaseModificationResolvedEvent event) {
        switch (event.getResolution()) {
            case "ACCEPTED" -> this.notificationService.queueNotification(event.getTenantId(),
                    this.messages.messageFor(MessageKeys.NOTIFICATION_MODIFY_ACCEPTED,
                            Placeholder.unparsed("region", event.getRegionId())));
            case "REJECTED" -> this.notificationService.queueNotification(event.getTenantId(),
                    this.messages.messageFor(MessageKeys.NOTIFICATION_MODIFY_REJECTED,
                            Placeholder.unparsed("region", event.getRegionId())));
            case "WITHDRAWN" -> {
                // Notify the party that did not withdraw.
                UUID target = LeaseholdRoles.LANDLORD.equals(event.getProposerRole())
                        ? event.getTenantId() : event.getLandlordId();
                this.notificationService.queueNotification(target,
                        this.messages.messageFor(MessageKeys.NOTIFICATION_MODIFY_WITHDRAWN,
                                Placeholder.unparsed("region", event.getRegionId())));
            }
            default -> { }
        }
    }

    @EventHandler
    public void onTerminationScheduled(@NotNull LeaseTerminationScheduledEvent event) {
        String date = event.getEffectiveDate().format(DateTimeFormatters.DATE_TIME);
        if (LeaseholdRoles.LANDLORD.equals(event.getTerminatedByRole())) {
            this.notificationService.queueNotification(event.getTenantId(),
                    this.messages.messageFor(MessageKeys.NOTIFICATION_TERMINATION_SCHEDULED_TENANT,
                            Placeholder.unparsed("region", event.getRegionId()),
                            Placeholder.unparsed("date", date)));
        } else {
            this.notificationService.queueNotification(event.getLandlordId(),
                    this.messages.messageFor(MessageKeys.NOTIFICATION_TERMINATION_SCHEDULED_LANDLORD,
                            Placeholder.unparsed("region", event.getRegionId()),
                            Placeholder.unparsed("date", date)));
        }
    }

    @EventHandler
    public void onTerminationCancelled(@NotNull LeaseTerminationCancelledEvent event) {
        // Notify the party that did not initiate the (now-cancelled) termination.
        UUID target = LeaseholdRoles.LANDLORD.equals(event.getTerminatedByRole())
                ? event.getTenantId() : event.getLandlordId();
        this.notificationService.queueNotification(target,
                this.messages.messageFor(MessageKeys.NOTIFICATION_TERMINATION_CANCELLED,
                        Placeholder.unparsed("region", event.getRegionId())));
    }

    @EventHandler
    public void onLeaseTerminated(@NotNull LeaseTerminatedEvent event) {
        this.notificationService.queueNotification(event.getTenantId(),
                this.messages.messageFor(MessageKeys.NOTIFICATION_LEASEHOLD_TERMINATED_TENANT,
                        Placeholder.unparsed("region", event.getRegionId()),
                        Placeholder.unparsed("refund", CurrencyFormatter.format(event.getRefund()))));
        this.notificationService.queueNotification(event.getLandlordId(),
                this.messages.messageFor(MessageKeys.NOTIFICATION_LEASEHOLD_TERMINATED_LANDLORD,
                        Placeholder.unparsed("region", event.getRegionId())));
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
}
