package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.api.CurrencyFormatter;
import io.github.md5sha256.realty.api.DurationFormatter;
import io.github.md5sha256.realty.api.RealtyPaperApi;
import io.github.md5sha256.realty.api.WorldGuardRegion;
import io.github.md5sha256.realty.api.event.RegionRentEvent;
import io.github.md5sha256.realty.api.event.RegionRentedEvent;
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

import java.time.Duration;

/**
 * Handles {@code /realty rent <region>}.
 *
 * <p>Permission: {@code realty.command.rent}.</p>
 */
public record RentCommand(
        @NotNull RealtyPaperApi api,
        @NotNull MessageContainer messages,
        @NotNull RealtyEventDispatch events
) implements CustomCommandBean.Single {

    @Override
    public @NotNull Command<? extends Source> command(@NotNull Command.Builder<Source> builder) {
        return builder
                .literal("rent")
                .permission("realty.command.rent")
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
        // Cancellable pre-event (main thread); a veto stops the action before the API is called.
        if (!events.fireSync(new RegionRentEvent(region, sender.getUniqueId()))) {
            sender.sendMessage(messages.messageFor(MessageKeys.COMMON_ACTION_CANCELLED));
            return;
        }
        api.rent(region, sender.getUniqueId()).thenAccept(result -> {
            switch (result) {
                case RealtyPaperApi.RentResult.Success success -> {
                    sender.sendMessage(messages.messageFor(MessageKeys.RENT_SUCCESS,
                            Placeholder.unparsed("region", success.regionId()),
                            Placeholder.unparsed("price", CurrencyFormatter.format(success.price())),
                            Placeholder.unparsed("duration",
                                    DurationFormatter.format(Duration.ofSeconds(success.durationSeconds())))));
                    // Post-event; fireSync hops to the main thread. RegionNotificationListener notifies the landlord.
                    events.fireSync(new RegionRentedEvent(region, sender.getUniqueId(),
                            success.landlordId(), success.price(), success.durationSeconds()));
                }
                case RealtyPaperApi.RentResult.NoLeaseholdContract noContract ->
                        sender.sendMessage(messages.messageFor(MessageKeys.RENT_NO_LEASEHOLD_CONTRACT,
                                Placeholder.unparsed("region", noContract.regionId())));
                case RealtyPaperApi.RentResult.AlreadyOccupied occupied ->
                        sender.sendMessage(messages.messageFor(MessageKeys.RENT_ALREADY_OCCUPIED,
                                Placeholder.unparsed("region", occupied.regionId())));
                case RealtyPaperApi.RentResult.NotAcceptingTenants notAccepting ->
                        sender.sendMessage(messages.messageFor(MessageKeys.RENT_NOT_ACCEPTING_TENANTS,
                                Placeholder.unparsed("region", notAccepting.regionId())));
                case RealtyPaperApi.RentResult.InsufficientFunds insufficient ->
                        sender.sendMessage(messages.messageFor(MessageKeys.RENT_INSUFFICIENT_FUNDS,
                                Placeholder.unparsed("price", CurrencyFormatter.format(insufficient.price())),
                                Placeholder.unparsed("balance", CurrencyFormatter.format(insufficient.balance()))));
                case RealtyPaperApi.RentResult.PaymentFailed failed ->
                        sender.sendMessage(messages.messageFor(MessageKeys.RENT_PAYMENT_FAILED,
                                Placeholder.unparsed("error", failed.error())));
                case RealtyPaperApi.RentResult.UpdateFailed updateFailed ->
                        sender.sendMessage(messages.messageFor(MessageKeys.RENT_UPDATE_FAILED,
                                Placeholder.unparsed("region", updateFailed.regionId())));
                case RealtyPaperApi.RentResult.Error error ->
                        sender.sendMessage(messages.messageFor(MessageKeys.RENT_ERROR,
                                Placeholder.unparsed("error", error.message())));
            }
        });
    }

}
