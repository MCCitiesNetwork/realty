package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.api.RealtyBackend;
import io.github.md5sha256.realty.api.RealtyPaperApi;
import io.github.md5sha256.realty.api.WorldGuardRegion;
import io.github.md5sha256.realty.api.event.LeaseModificationProposedEvent;
import io.github.md5sha256.realty.api.event.LeaseModificationResolvedEvent;
import io.github.md5sha256.realty.api.event.LeaseModifyProposeEvent;
import io.github.md5sha256.realty.command.util.DurationParser;
import io.github.md5sha256.realty.command.util.ParseBounds;
import io.github.md5sha256.realty.command.util.WorldGuardRegionResolver;
import io.github.md5sha256.realty.event.RealtyEventDispatch;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.localisation.MessageKeys;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.paper.util.sender.Source;
import org.incendo.cloud.parser.standard.DoubleParser;
import org.incendo.cloud.parser.standard.IntegerParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Groups the rental modification subcommands under {@code /realty modify}. Changes proposed here take
 * effect on the tenant's next renewal rather than instantly:
 *
 * <ul>
 *   <li>{@code /realty modify price|duration|maxextensions <value> [region]} — propose new terms</li>
 *   <li>{@code /realty modify accept|reject [region]} — landlord resolves a tenant's proposal</li>
 *   <li>{@code /realty modify withdraw [region]} — proposer withdraws their pending proposal</li>
 * </ul>
 *
 * <p>A landlord's proposal applies automatically when the tenant next renews (the tenant declines by
 * not renewing). A tenant's proposal is a request the landlord must explicitly accept. Permission base:
 * {@code realty.command.modify.*} ({@code .others} grants the admin override).</p>
 */
public record ModifyCommandGroup(
        @NotNull RealtyPaperApi api,
        @NotNull MessageContainer messages,
        @NotNull RealtyEventDispatch events
) implements CustomCommandBean {

    @Override
    public @NotNull List<Command<? extends Source>> commands(@NotNull Command.Builder<Source> builder) {
        var base = builder.literal("modify");
        return List.of(
                base.literal("price")
                        .permission("realty.command.modify.price")
                        .required("price", DoubleParser.doubleParser(ParseBounds.MIN_STRICTLY_POSITIVE,
                                Double.MAX_VALUE))
                        .optional("region", WorldGuardRegionResolver.worldGuardRegionResolver())
                        .handler(ctx -> executePropose(ctx, ctx.get("price"), null, null))
                        .build(),
                base.literal("duration")
                        .permission("realty.command.modify.duration")
                        .required("duration", DurationParser.duration())
                        .optional("region", WorldGuardRegionResolver.worldGuardRegionResolver())
                        .handler(ctx -> executePropose(ctx, null,
                                ((Duration) ctx.get("duration")).toSeconds(), null))
                        .build(),
                base.literal("maxextensions")
                        .permission("realty.command.modify.maxextensions")
                        .required("maxextensions", IntegerParser.integerParser(0))
                        .optional("region", WorldGuardRegionResolver.worldGuardRegionResolver())
                        .handler(ctx -> executePropose(ctx, null, null, ctx.<Integer>get("maxextensions")))
                        .build(),
                base.literal("accept")
                        .permission("realty.command.modify.accept")
                        .optional("region", WorldGuardRegionResolver.worldGuardRegionResolver())
                        .handler(ctx -> executeResolve(ctx, ResolveAction.ACCEPT))
                        .build(),
                base.literal("reject")
                        .permission("realty.command.modify.reject")
                        .optional("region", WorldGuardRegionResolver.worldGuardRegionResolver())
                        .handler(ctx -> executeResolve(ctx, ResolveAction.REJECT))
                        .build(),
                base.literal("withdraw")
                        .permission("realty.command.modify.withdraw")
                        .optional("region", WorldGuardRegionResolver.worldGuardRegionResolver())
                        .handler(ctx -> executeResolve(ctx, ResolveAction.WITHDRAW))
                        .build()
        );
    }

    /** The three ways to resolve a pending proposal, each carrying its success message and resolution name. */
    private enum ResolveAction {
        ACCEPT(MessageKeys.MODIFY_ACCEPT_SUCCESS, "ACCEPTED"),
        REJECT(MessageKeys.MODIFY_REJECT_SUCCESS, "REJECTED"),
        WITHDRAW(MessageKeys.MODIFY_WITHDRAW_SUCCESS, "WITHDRAWN");

        private final String successKey;
        private final String resolution;

        ResolveAction(String successKey, String resolution) {
            this.successKey = successKey;
            this.resolution = resolution;
        }
    }

    private void executePropose(@NotNull CommandContext<Source> ctx,
                                @Nullable Double price, @Nullable Long durationSeconds,
                                @Nullable Integer maxExtensions) {
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
        if (!events.fireSync(new LeaseModifyProposeEvent(region, sender.getUniqueId()))) {
            sender.sendMessage(messages.messageFor(MessageKeys.COMMON_ACTION_CANCELLED));
            return;
        }
        boolean bypass = sender.hasPermission("realty.command.modify.others");
        String regionId = region.region().getId();
        api.proposeModification(regionId, region.world().getUID(), sender.getUniqueId(), bypass,
                price, durationSeconds, maxExtensions).thenAccept(result -> {
            switch (result) {
                case RealtyBackend.ProposeModificationResult.Success success -> {
                    String key = success.active()
                            ? MessageKeys.MODIFY_PROPOSE_SUCCESS_LANDLORD
                            : MessageKeys.MODIFY_PROPOSE_SUCCESS_TENANT;
                    sender.sendMessage(messages.messageFor(key, Placeholder.unparsed("region", regionId)));
                    events.fireSync(new LeaseModificationProposedEvent(region, success.proposerRole(),
                            sender.getUniqueId(), success.landlordId(), success.tenantId(), success.active()));
                }
                case RealtyBackend.ProposeModificationResult.NoLeaseholdContract ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.MODIFY_NO_LEASEHOLD_CONTRACT,
                                Placeholder.unparsed("region", regionId)));
                case RealtyBackend.ProposeModificationResult.NotOccupied ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.MODIFY_NOT_OCCUPIED,
                                Placeholder.unparsed("region", regionId)));
                case RealtyBackend.ProposeModificationResult.Terminating ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.MODIFY_TERMINATING,
                                Placeholder.unparsed("region", regionId)));
                case RealtyBackend.ProposeModificationResult.NotAuthorized ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.MODIFY_NOT_AUTHORIZED,
                                Placeholder.unparsed("region", regionId)));
                case RealtyBackend.ProposeModificationResult.UpdateFailed ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.MODIFY_UPDATE_FAILED,
                                Placeholder.unparsed("region", regionId)));
            }
        }).exceptionally(ex -> {
            sender.sendMessage(messages.messageFor(MessageKeys.MODIFY_ERROR,
                    Placeholder.unparsed("error", String.valueOf(ex.getMessage()))));
            return null;
        });
    }

    private void executeResolve(@NotNull CommandContext<Source> ctx, @NotNull ResolveAction action) {
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
        boolean bypass = sender.hasPermission("realty.command.modify.others");
        String regionId = region.region().getId();
        UUID worldId = region.world().getUID();
        UUID actorId = sender.getUniqueId();
        var future = switch (action) {
            case ACCEPT -> api.acceptModification(regionId, worldId, actorId, bypass);
            case REJECT -> api.rejectModification(regionId, worldId, actorId, bypass);
            case WITHDRAW -> api.withdrawModification(regionId, worldId, actorId, bypass);
        };
        future.thenAccept(result -> {
            switch (result) {
                case RealtyBackend.ResolveModificationResult.Success success -> {
                    sender.sendMessage(messages.messageFor(action.successKey,
                            Placeholder.unparsed("region", regionId)));
                    events.fireSync(new LeaseModificationResolvedEvent(region, action.resolution,
                            success.proposerRole(), success.landlordId(), success.tenantId()));
                }
                case RealtyBackend.ResolveModificationResult.NoLeaseholdContract ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.MODIFY_NO_LEASEHOLD_CONTRACT,
                                Placeholder.unparsed("region", regionId)));
                case RealtyBackend.ResolveModificationResult.NoPendingProposal ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.MODIFY_NO_PENDING_PROPOSAL,
                                Placeholder.unparsed("region", regionId)));
                case RealtyBackend.ResolveModificationResult.NotTenantProposal ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.MODIFY_NOT_TENANT_PROPOSAL,
                                Placeholder.unparsed("region", regionId)));
                case RealtyBackend.ResolveModificationResult.NotAuthorized ignored ->
                        sender.sendMessage(messages.messageFor(
                                action == ResolveAction.WITHDRAW
                                        ? MessageKeys.MODIFY_NOT_PROPOSER
                                        : MessageKeys.MODIFY_NOT_LANDLORD,
                                Placeholder.unparsed("region", regionId)));
                case RealtyBackend.ResolveModificationResult.UpdateFailed ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.MODIFY_UPDATE_FAILED,
                                Placeholder.unparsed("region", regionId)));
            }
        }).exceptionally(ex -> {
            sender.sendMessage(messages.messageFor(MessageKeys.MODIFY_ERROR,
                    Placeholder.unparsed("error", String.valueOf(ex.getMessage()))));
            return null;
        });
    }
}
