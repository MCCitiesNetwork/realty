package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.api.ExecutorState;
import io.github.md5sha256.realty.api.FeeSide;
import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.api.RealtyBackend;
import io.github.md5sha256.realty.api.RealtyPaperApi;
import io.github.md5sha256.realty.api.WorldGuardRegion;
import io.github.md5sha256.realty.api.event.RealtyNotificationEvent;
import io.github.md5sha256.realty.auth.ActorContexts;
import io.github.md5sha256.realty.command.util.FeeQuoteFormatter;
import io.github.md5sha256.realty.command.util.ParseBounds;
import io.github.md5sha256.realty.command.util.PartyFlag;
import io.github.md5sha256.realty.command.util.PartyFlags;
import io.github.md5sha256.realty.command.util.PartyNameParser;
import io.github.md5sha256.realty.command.util.PartyResolver;
import io.github.md5sha256.realty.command.util.RegionOrFlagParser;
import io.github.md5sha256.realty.command.util.WorldGuardRegionResolver;
import io.github.md5sha256.realty.event.RealtyEventDispatch;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.localisation.MessageKeys;
import io.github.md5sha256.realty.notify.PartyRecipients;
import io.github.md5sha256.realty.util.PartyNames;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.paper.util.sender.Source;
import org.incendo.cloud.parser.flag.CommandFlag;
import org.incendo.cloud.parser.standard.DoubleParser;
import org.incendo.cloud.parser.standard.EnumParser;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Groups the player fee subcommands under {@code /realty fee}.
 *
 * <ul>
 *   <li>{@code /realty fee set <percent> <buyer|seller> [region] [--authority]}</li>
 *   <li>{@code /realty fee remove [region] [--authority]}</li>
 *   <li>{@code /realty fee list [region]}</li>
 *   <li>{@code /realty fee approve <recipient> [region] [type flag]}</li>
 *   <li>{@code /realty fee reject <recipient> [region] [type flag]}</li>
 * </ul>
 *
 * <p>{@code --authority} pays the fee to the region's authority instead of the player.</p>
 */
public record FeeCommandGroup(
        @NotNull RealtyPaperApi api,
        @NotNull ActorContexts actors,
        @NotNull PartyResolver partyResolver,
        @NotNull SuggestionProvider<Source> partySuggestions,
        @NotNull ExecutorState executorState,
        @NotNull MessageContainer messages,
        @NotNull PartyNames partyNames,
        @NotNull PartyRecipients recipients,
        @NotNull RealtyEventDispatch events,
        @NotNull Logger logger
) implements CustomCommandBean {

    static final CommandFlag<Void> AUTHORITY_FLAG = CommandFlag.<Source>builder("authority").build();

    @Override
    public @NotNull List<Command<? extends Source>> commands(@NotNull Command.Builder<Source> builder) {
        var base = builder.literal("fee");
        return List.of(
                base.literal("set")
                        .permission("realty.command.fee.set")
                        .required("percent", DoubleParser.doubleParser(ParseBounds.MIN_STRICTLY_POSITIVE, 100))
                        .required("side", EnumParser.enumParser(FeeSide.class))
                        .optional("region", RegionOrFlagParser.regionOrFlag())
                        .flag(AUTHORITY_FLAG)
                        .handler(this::executeSet)
                        .build(),
                base.literal("remove")
                        .permission("realty.command.fee.remove")
                        .optional("region", RegionOrFlagParser.regionOrFlag())
                        .flag(AUTHORITY_FLAG)
                        .handler(this::executeRemove)
                        .build(),
                base.literal("list")
                        .permission("realty.command.fee.list")
                        .optional("region", RegionOrFlagParser.regionOrFlag())
                        .handler(this::executeList)
                        .build(),
                PartyFlags.addTo(base.literal("approve")
                                .permission("realty.command.fee.approve")
                                .required("recipient", PartyNameParser.partyName(), partySuggestions)
                                .optional("region", RegionOrFlagParser.regionOrFlag()))
                        .handler(ctx -> executeResolve(ctx, true))
                        .build(),
                PartyFlags.addTo(base.literal("reject")
                                .permission("realty.command.fee.reject")
                                .required("recipient", PartyNameParser.partyName(), partySuggestions)
                                .optional("region", RegionOrFlagParser.regionOrFlag()))
                        .handler(ctx -> executeResolve(ctx, false))
                        .build()
        );
    }

    static @Nullable WorldGuardRegion regionOf(@NotNull CommandContext<Source> ctx, @NotNull CommandSender sender) {
        return ctx.<Optional<WorldGuardRegion>>optional("region")
                .flatMap(Function.identity())
                .orElseGet(() -> sender instanceof Player player
                        ? WorldGuardRegionResolver.resolveAtLocation(player.getLocation()) : null);
    }

    static @NotNull String sideName(@NotNull FeeSide side) {
        return side.name().toLowerCase(Locale.ROOT);
    }

    // ── /realty fee set <percent> <buyer|seller> [region] [--authority] ──

    private void executeSet(@NotNull CommandContext<Source> ctx) {
        if (!(ctx.sender().source() instanceof Player sender)) {
            ctx.sender().source().sendMessage(messages.messageFor(MessageKeys.COMMON_PLAYERS_ONLY));
            return;
        }
        double percent = ctx.get("percent");
        FeeSide side = ctx.get("side");
        boolean asAuthority = ctx.flags().hasFlag(AUTHORITY_FLAG);
        WorldGuardRegion region = regionOf(ctx, sender);
        if (region == null) {
            sender.sendMessage(messages.messageFor(MessageKeys.ERROR_NO_REGION));
            return;
        }
        String regionId = region.region().getId();
        CompletableFuture.supplyAsync(() -> actors.forRegion(sender, false, region), executorState.dbExec())
                .thenComposeAsync(actor -> api.setFee(region, actor, percent, side, asAuthority),
                        executorState.mainThreadExec())
                .thenAccept(result -> {
                    switch (result) {
                        case RealtyBackend.SetFeeResult.Success success -> {
                            String recipient = partyNames.display(success.recipient());
                            sender.sendMessage(messages.messageFor(success.needsApproval()
                                            ? MessageKeys.FEE_SET_AWAITING_APPROVAL : MessageKeys.FEE_SET,
                                    Placeholder.unparsed("region", regionId),
                                    Placeholder.unparsed("percent", FeeQuoteFormatter.percent(percent)),
                                    Placeholder.unparsed("side", sideName(side)),
                                    Placeholder.unparsed("recipient", recipient)));
                            if (success.needsApproval()) {
                                notifySeller(region, messages.messageFor(MessageKeys.NOTIFICATION_FEE_AWAITING_APPROVAL,
                                        Placeholder.unparsed("player", sender.getName()),
                                        Placeholder.unparsed("percent", FeeQuoteFormatter.percent(percent)),
                                        Placeholder.unparsed("region", regionId),
                                        Placeholder.unparsed("recipient", recipient)));
                            }
                        }
                        case RealtyBackend.SetFeeResult.NoFreehold ignored -> noFreehold(sender, regionId);
                        case RealtyBackend.SetFeeResult.NotAllowed ignored ->
                                sender.sendMessage(messages.messageFor(MessageKeys.FEE_NOT_ALLOWED,
                                        Placeholder.unparsed("region", regionId)));
                        case RealtyBackend.SetFeeResult.NotAuthorityManager ignored ->
                                notAuthorityManager(sender, regionId);
                        case RealtyBackend.SetFeeResult.NoChange ignored ->
                                sender.sendMessage(messages.messageFor(MessageKeys.FEE_NO_CHANGE,
                                        Placeholder.unparsed("region", regionId)));
                        case RealtyBackend.SetFeeResult.Cancelled ignored ->
                                sender.sendMessage(messages.messageFor(MessageKeys.COMMON_ACTION_CANCELLED));
                        case RealtyBackend.SetFeeResult.SaleOpen ignored ->
                                sender.sendMessage(messages.messageFor(MessageKeys.FEE_SALE_OPEN,
                                        Placeholder.unparsed("region", regionId)));
                    }
                }).exceptionally(ex -> error(sender, ex));
    }

    // ── /realty fee remove [region] [--authority] ──

    private void executeRemove(@NotNull CommandContext<Source> ctx) {
        if (!(ctx.sender().source() instanceof Player sender)) {
            ctx.sender().source().sendMessage(messages.messageFor(MessageKeys.COMMON_PLAYERS_ONLY));
            return;
        }
        boolean asAuthority = ctx.flags().hasFlag(AUTHORITY_FLAG);
        WorldGuardRegion region = regionOf(ctx, sender);
        if (region == null) {
            sender.sendMessage(messages.messageFor(MessageKeys.ERROR_NO_REGION));
            return;
        }
        String regionId = region.region().getId();
        CompletableFuture.supplyAsync(() -> actors.forRegion(sender, false, region), executorState.dbExec())
                .thenComposeAsync(actor -> api.removeFee(region, actor, asAuthority), executorState.mainThreadExec())
                .thenAccept(result -> {
                    switch (result) {
                        case RealtyBackend.RemoveFeeResult.Success ignored ->
                                sender.sendMessage(messages.messageFor(MessageKeys.FEE_REMOVED,
                                        Placeholder.unparsed("region", regionId)));
                        case RealtyBackend.RemoveFeeResult.NoFreehold ignored -> noFreehold(sender, regionId);
                        case RealtyBackend.RemoveFeeResult.NoFee ignored ->
                                sender.sendMessage(messages.messageFor(MessageKeys.FEE_NO_FEE,
                                        Placeholder.unparsed("region", regionId)));
                        case RealtyBackend.RemoveFeeResult.NotAuthorityManager ignored ->
                                notAuthorityManager(sender, regionId);
                    }
                }).exceptionally(ex -> error(sender, ex));
    }

    // ── /realty fee list [region] ──

    private void executeList(@NotNull CommandContext<Source> ctx) {
        CommandSender sender = ctx.sender().source();
        WorldGuardRegion region = regionOf(ctx, sender);
        if (region == null) {
            sender.sendMessage(messages.messageFor(MessageKeys.ERROR_NO_REGION));
            return;
        }
        String regionId = region.region().getId();
        api.getFreeholdContract(regionId, region.world().getUID())
                .thenCombine(api.listFees(region), (freehold, fees) -> {
                    if (freehold == null) {
                        return messages.messageFor(MessageKeys.FEE_NO_FREEHOLD, Placeholder.unparsed("region", regionId));
                    }
                    if (fees.isEmpty()) {
                        return messages.messageFor(MessageKeys.FEE_LIST_EMPTY, Placeholder.unparsed("region", regionId));
                    }
                    Component output = messages.messageFor(MessageKeys.FEE_LIST_HEADER,
                            Placeholder.unparsed("region", regionId));
                    for (RealtyBackend.FeeListing fee : fees) {
                        // A levy paid to the authority names no recipient of its own.
                        Party recipient = fee.recipient() != null ? fee.recipient() : freehold.authority();
                        output = output.appendNewline().append(messages.messageFor(
                                fee.approved() ? MessageKeys.FEE_LIST_ENTRY : MessageKeys.FEE_LIST_ENTRY_PENDING,
                                Placeholder.unparsed("source", fee.source()),
                                Placeholder.unparsed("rate", fee.rate()),
                                Placeholder.unparsed("side", sideName(fee.side())),
                                Placeholder.unparsed("recipient", partyNames.display(recipient))));
                    }
                    return output;
                })
                .thenAccept(sender::sendMessage)
                .exceptionally(ex -> error(sender, ex));
    }

    // ── /realty fee approve|reject <recipient> [region] ──

    private void executeResolve(@NotNull CommandContext<Source> ctx, boolean approve) {
        if (!(ctx.sender().source() instanceof Player sender)) {
            ctx.sender().source().sendMessage(messages.messageFor(MessageKeys.COMMON_PLAYERS_ONLY));
            return;
        }
        if (!(PartyFlags.read(ctx) instanceof PartyFlags.Read.One(PartyFlag flag))) {
            sender.sendMessage(messages.messageFor(MessageKeys.PARTY_MULTIPLE_TYPE_FLAGS));
            return;
        }
        String recipientName = ctx.get("recipient");
        WorldGuardRegion region = regionOf(ctx, sender);
        if (region == null) {
            sender.sendMessage(messages.messageFor(MessageKeys.ERROR_NO_REGION));
            return;
        }
        String regionId = region.region().getId();
        PartyFlags.resolveThen(partyResolver, executorState, messages, sender, recipientName, flag, recipient ->
                CompletableFuture.supplyAsync(() -> actors.forRegion(sender, false, region), executorState.dbExec())
                        .thenComposeAsync(actor -> approve
                                ? api.approveFee(region, actor, recipient)
                                : api.rejectFee(region, actor, recipient), executorState.mainThreadExec())
                        .thenAccept(result -> {
                            String name = partyNames.display(recipient);
                            switch (result) {
                                case RealtyBackend.ResolveFeeResult.Success success ->
                                        sender.sendMessage(messages.messageFor(
                                                approve ? MessageKeys.FEE_APPROVED : MessageKeys.FEE_REJECTED,
                                                Placeholder.unparsed("percent", FeeQuoteFormatter.percent(success.percent())),
                                                Placeholder.unparsed("recipient", name),
                                                Placeholder.unparsed("region", regionId)));
                                case RealtyBackend.ResolveFeeResult.NoFreehold ignored -> noFreehold(sender, regionId);
                                case RealtyBackend.ResolveFeeResult.NotSeller ignored ->
                                        sender.sendMessage(messages.messageFor(MessageKeys.FEE_NOT_SELLER,
                                                Placeholder.unparsed("region", regionId)));
                                case RealtyBackend.ResolveFeeResult.NoPendingFee ignored ->
                                        sender.sendMessage(messages.messageFor(MessageKeys.FEE_NO_PENDING_FEE,
                                                Placeholder.unparsed("recipient", name),
                                                Placeholder.unparsed("region", regionId)));
                            }
                        }).exceptionally(ex -> error(sender, ex)));
    }

    /**
     * Tells the seller, the titleholder or else the authority, that a fee waits for their
     * approval. A failure is logged, never shown: the fee itself was set.
     */
    private void notifySeller(@NotNull WorldGuardRegion region, @NotNull Component message) {
        api.getFreeholdContract(region.region().getId(), region.world().getUID())
                .thenAcceptAsync(freehold -> {
                    if (freehold == null) {
                        return;
                    }
                    Party seller = freehold.titleHolder().orElse(freehold.authority());
                    List<UUID> people = recipients.expand(seller);
                    if (!people.isEmpty()) {
                        events.fireSync(new RealtyNotificationEvent(people,
                                MessageKeys.NOTIFICATION_FEE_AWAITING_APPROVAL, message, region));
                    }
                }, executorState.dbExec())
                .exceptionally(ex -> {
                    logger.log(Level.WARNING, "Could not tell the seller of " + region.region().getId()
                            + " about a fee awaiting approval", ex);
                    return null;
                });
    }

    private void noFreehold(@NotNull CommandSender sender, @NotNull String regionId) {
        sender.sendMessage(messages.messageFor(MessageKeys.FEE_NO_FREEHOLD, Placeholder.unparsed("region", regionId)));
    }

    private void notAuthorityManager(@NotNull CommandSender sender, @NotNull String regionId) {
        sender.sendMessage(messages.messageFor(MessageKeys.FEE_NOT_AUTHORITY_MANAGER,
                Placeholder.unparsed("region", regionId)));
    }

    private @Nullable Void error(@NotNull CommandSender sender, @NotNull Throwable ex) {
        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
        sender.sendMessage(messages.messageFor(MessageKeys.FEE_ERROR,
                Placeholder.unparsed("error", String.valueOf(cause.getMessage()))));
        return null;
    }
}
