package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.api.CurrencyFormatter;
import io.github.md5sha256.realty.api.RealtyPaperApi;
import io.github.md5sha256.realty.api.WorldGuardRegion;
import io.github.md5sha256.realty.api.event.RegionUnrentEvent;
import io.github.md5sha256.realty.api.event.RegionUnrentedEvent;
import io.github.md5sha256.realty.command.util.WorldGuardRegionResolver;
import io.github.md5sha256.realty.event.RealtyEventDispatch;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.localisation.MessageKeys;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.incendo.cloud.paper.util.sender.Source;

import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;

/**
 * Handles {@code /realty unrent [region]}.
 *
 * <p>Removes the tenant from a leased region, clears WorldGuard members,
 * provides a prorated refund, and updates the region sign.
 * Permission: {@code realty.command.unrent}.</p>
 */
public record UnrentCommand(
        @NotNull RealtyPaperApi api,
        @NotNull MessageContainer messages,
        @NotNull RealtyEventDispatch events
) implements CustomCommandBean.Single {

    @Override
    public @NotNull Command<? extends Source> command(@NotNull Command.Builder<Source> builder) {
        return builder
                .literal("unrent")
                .permission("realty.command.unrent")
                .optional("region", WorldGuardRegionResolver.worldGuardRegionResolver())
                .handler(this::execute)
                .build();
    }

    private void execute(@NotNull CommandContext<Source> ctx) {
        if (!(ctx.sender().source() instanceof Player sender)) {
            ctx.sender().source().sendMessage(messages.messageFor(MessageKeys.COMMON_PLAYERS_ONLY));
            return;
        }
        WorldGuardRegion region = ctx.<WorldGuardRegion>optional("region")
                .orElseGet(() -> WorldGuardRegionResolver.resolveAtLocation(sender.getLocation()));
        if (region == null) {
            sender.sendMessage(messages.messageFor(MessageKeys.ERROR_NO_REGION));
            return;
        }
        if (!region.region().getOwners().contains(sender.getUniqueId())) {
            sender.sendMessage(messages.messageFor(MessageKeys.UNRENT_NOT_TENANT,
                    Placeholder.unparsed("region", region.region().getId())));
            return;
        }
        // Cancellable pre-event (main thread); a veto stops the action before the API is called.
        if (!events.fireSync(new RegionUnrentEvent(region, sender.getUniqueId()))) {
            sender.sendMessage(messages.messageFor(MessageKeys.COMMON_ACTION_CANCELLED));
            return;
        }
        api.unrent(region, sender.getUniqueId()).thenAccept(result -> {
            switch (result) {
                case RealtyPaperApi.UnrentResult.Success success -> {
                    sender.sendMessage(messages.messageFor(MessageKeys.UNRENT_SUCCESS,
                            Placeholder.unparsed("region", success.regionId()),
                            Placeholder.unparsed("refund", CurrencyFormatter.format(success.refund()))));
                    // Post-event; fireSync hops to the main thread. RegionNotificationListener notifies the landlord.
                    events.fireSync(new RegionUnrentedEvent(region, sender.getUniqueId(),
                            success.landlordId(), success.refund()));
                }
                case RealtyPaperApi.UnrentResult.NoLeaseholdContract noContract ->
                        sender.sendMessage(messages.messageFor(MessageKeys.UNRENT_NO_LEASEHOLD_CONTRACT,
                                Placeholder.unparsed("region", noContract.regionId())));
                case RealtyPaperApi.UnrentResult.RefundFailed failed ->
                        sender.sendMessage(messages.messageFor(MessageKeys.UNRENT_REFUND_FAILED,
                                Placeholder.unparsed("error", failed.error())));
                case RealtyPaperApi.UnrentResult.UpdateFailed updateFailed ->
                        sender.sendMessage(messages.messageFor(MessageKeys.UNRENT_UPDATE_FAILED,
                                Placeholder.unparsed("region", updateFailed.regionId())));
                case RealtyPaperApi.UnrentResult.Error error ->
                        sender.sendMessage(messages.messageFor(MessageKeys.UNRENT_ERROR,
                                Placeholder.unparsed("error", error.message())));
            }
        });
    }

}
