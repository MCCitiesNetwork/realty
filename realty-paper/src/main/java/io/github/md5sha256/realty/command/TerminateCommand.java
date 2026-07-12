package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.api.CurrencyFormatter;
import io.github.md5sha256.realty.api.DateTimeFormatters;
import io.github.md5sha256.realty.api.RealtyPaperApi;
import io.github.md5sha256.realty.api.WorldGuardRegion;
import io.github.md5sha256.realty.api.event.LeaseTerminateEvent;
import io.github.md5sha256.realty.api.event.LeaseTerminationCancelledEvent;
import io.github.md5sha256.realty.api.event.LeaseTerminationScheduledEvent;
import io.github.md5sha256.realty.command.util.WorldGuardRegionResolver;
import io.github.md5sha256.realty.event.RealtyEventDispatch;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.localisation.MessageKeys;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.paper.util.sender.Source;
import org.incendo.cloud.parser.flag.CommandFlag;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Groups the termination subcommands under {@code /realty terminate}:
 *
 * <ul>
 *   <li>{@code /realty terminate [region]} — schedule an early termination with the configured notice</li>
 *   <li>{@code /realty terminate cancel [region]} — cancel a pending termination (initiator only)</li>
 * </ul>
 *
 * <p>Either the landlord or the tenant may terminate; a tenant pays for any whole extensions needed to
 * cover the notice period, a landlord does not. Permission: {@code realty.command.terminate} (with
 * {@code realty.command.terminate.others} as the admin override).</p>
 */
public record TerminateCommand(
        @NotNull RealtyPaperApi api,
        @NotNull MessageContainer messages,
        @NotNull RealtyEventDispatch events
) implements CustomCommandBean {

    /** {@code --now} skips the notice period (immediate end + full refund); gated by {@code realty.command.terminate.now}. */
    private static final CommandFlag<Void> NOW_FLAG = CommandFlag.<Source>builder("now").build();

    @Override
    public @NotNull List<Command<? extends Source>> commands(@NotNull Command.Builder<Source> builder) {
        var base = builder.literal("terminate");
        return List.of(
                base.permission("realty.command.terminate")
                        .optional("region", WorldGuardRegionResolver.worldGuardRegionResolver())
                        .flag(NOW_FLAG)
                        .handler(this::executeTerminate)
                        .build(),
                base.literal("cancel")
                        .permission("realty.command.terminate")
                        .optional("region", WorldGuardRegionResolver.worldGuardRegionResolver())
                        .handler(this::executeCancel)
                        .build()
        );
    }

    private void executeTerminate(@NotNull CommandContext<Source> ctx) {
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
        boolean immediate = ctx.flags().hasFlag(NOW_FLAG);
        if (immediate && !sender.hasPermission("realty.command.terminate.now")) {
            sender.sendMessage(messages.messageFor(MessageKeys.TERMINATE_NOW_NO_PERMISSION));
            return;
        }
        // Cancellable pre-event (main thread); a veto stops the action before the API is called.
        if (!events.fireSync(new LeaseTerminateEvent(region, sender.getUniqueId()))) {
            sender.sendMessage(messages.messageFor(MessageKeys.COMMON_ACTION_CANCELLED));
            return;
        }
        boolean bypass = sender.hasPermission("realty.command.terminate.others");
        String regionId = region.region().getId();
        api.terminate(region, sender.getUniqueId(), bypass, immediate).thenAccept(result -> {
            switch (result) {
                case RealtyPaperApi.TerminateResult.Success success -> {
                    String date = success.effectiveDate().format(DateTimeFormatters.DATE_TIME);
                    if (success.charged() > 0) {
                        sender.sendMessage(messages.messageFor(MessageKeys.TERMINATE_SUCCESS_CHARGED,
                                Placeholder.unparsed("region", success.regionId()),
                                Placeholder.unparsed("date", date),
                                Placeholder.unparsed("charged", CurrencyFormatter.format(success.charged()))));
                    } else {
                        sender.sendMessage(messages.messageFor(MessageKeys.TERMINATE_SUCCESS,
                                Placeholder.unparsed("region", success.regionId()),
                                Placeholder.unparsed("date", date)));
                    }
                    events.fireSync(new LeaseTerminationScheduledEvent(region, success.landlordId(),
                            success.tenantId(), success.terminatedByRole(), success.effectiveDate(),
                            success.charged()));
                }
                case RealtyPaperApi.TerminateResult.NoLeaseholdContract ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.TERMINATE_NO_LEASEHOLD_CONTRACT,
                                Placeholder.unparsed("region", regionId)));
                case RealtyPaperApi.TerminateResult.NotOccupied ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.TERMINATE_NOT_OCCUPIED,
                                Placeholder.unparsed("region", regionId)));
                case RealtyPaperApi.TerminateResult.AlreadyTerminating ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.TERMINATE_ALREADY_TERMINATING,
                                Placeholder.unparsed("region", regionId)));
                case RealtyPaperApi.TerminateResult.NotAuthorized ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.TERMINATE_NOT_AUTHORIZED,
                                Placeholder.unparsed("region", regionId)));
                case RealtyPaperApi.TerminateResult.InsufficientFunds funds ->
                        sender.sendMessage(messages.messageFor(MessageKeys.TERMINATE_INSUFFICIENT_FUNDS,
                                Placeholder.unparsed("price", CurrencyFormatter.format(funds.price())),
                                Placeholder.unparsed("balance", CurrencyFormatter.format(funds.balance()))));
                case RealtyPaperApi.TerminateResult.PaymentFailed failed ->
                        sender.sendMessage(messages.messageFor(MessageKeys.TERMINATE_PAYMENT_FAILED,
                                Placeholder.unparsed("error", failed.error())));
                case RealtyPaperApi.TerminateResult.UpdateFailed ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.TERMINATE_UPDATE_FAILED,
                                Placeholder.unparsed("region", regionId)));
                case RealtyPaperApi.TerminateResult.Error error ->
                        sender.sendMessage(messages.messageFor(MessageKeys.TERMINATE_ERROR,
                                Placeholder.unparsed("error", error.message())));
            }
        });
    }

    private void executeCancel(@NotNull CommandContext<Source> ctx) {
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
        boolean bypass = sender.hasPermission("realty.command.terminate.others");
        String regionId = region.region().getId();
        api.cancelTermination(regionId, region.world().getUID(), sender.getUniqueId(), bypass)
                .thenAccept(result -> {
                    switch (result) {
                        case io.github.md5sha256.realty.api.RealtyBackend.CancelTerminationResult.Success success -> {
                            sender.sendMessage(messages.messageFor(MessageKeys.TERMINATE_CANCEL_SUCCESS,
                                    Placeholder.unparsed("region", regionId)));
                            events.fireSync(new LeaseTerminationCancelledEvent(region, success.landlordId(),
                                    success.tenantId(), success.terminatedByRole()));
                        }
                        case io.github.md5sha256.realty.api.RealtyBackend.CancelTerminationResult.NoLeaseholdContract ignored ->
                                sender.sendMessage(messages.messageFor(MessageKeys.TERMINATE_CANCEL_NO_LEASEHOLD_CONTRACT,
                                        Placeholder.unparsed("region", regionId)));
                        case io.github.md5sha256.realty.api.RealtyBackend.CancelTerminationResult.NotTerminating ignored ->
                                sender.sendMessage(messages.messageFor(MessageKeys.TERMINATE_CANCEL_NOT_TERMINATING,
                                        Placeholder.unparsed("region", regionId)));
                        case io.github.md5sha256.realty.api.RealtyBackend.CancelTerminationResult.NotAuthorized ignored ->
                                sender.sendMessage(messages.messageFor(MessageKeys.TERMINATE_CANCEL_NOT_AUTHORIZED,
                                        Placeholder.unparsed("region", regionId)));
                        case io.github.md5sha256.realty.api.RealtyBackend.CancelTerminationResult.UpdateFailed ignored ->
                                sender.sendMessage(messages.messageFor(MessageKeys.TERMINATE_CANCEL_UPDATE_FAILED,
                                        Placeholder.unparsed("region", regionId)));
                    }
                }).exceptionally(ex -> {
                    sender.sendMessage(messages.messageFor(MessageKeys.TERMINATE_CANCEL_ERROR,
                            Placeholder.unparsed("error", String.valueOf(ex.getMessage()))));
                    return null;
                });
    }
}
