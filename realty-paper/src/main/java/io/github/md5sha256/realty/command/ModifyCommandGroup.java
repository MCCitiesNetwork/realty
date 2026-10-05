package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.api.ExecutorState;
import io.github.md5sha256.realty.auth.ActorContexts;
import io.github.md5sha256.realty.api.LeaseholdModificationStatus;
import io.github.md5sha256.realty.api.RealtyBackend;
import io.github.md5sha256.realty.api.RealtyPaperApi;
import io.github.md5sha256.realty.api.WorldGuardRegion;
import io.github.md5sha256.realty.api.event.LeaseModificationResolvedEvent;
import io.github.md5sha256.realty.command.util.LeaseholdChangeSummary;
import io.github.md5sha256.realty.command.util.WorldGuardRegionResolver;
import io.github.md5sha256.realty.database.entity.LeaseholdModificationView;
import io.github.md5sha256.realty.event.RealtyEventDispatch;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.util.PartyNames;
import io.github.md5sha256.realty.localisation.MessageKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.paper.util.sender.Source;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Groups the proposal inbox subcommands under {@code /realty modify}. Terms are proposed with
 * {@code /realty set price|duration|maxextensions}; the subcommands here resolve and list those proposals:
 *
 * <ul>
 *   <li>{@code /realty modify accept|reject [region]} — landlord resolves a tenant's proposal</li>
 *   <li>{@code /realty modify withdraw [region]} — proposer withdraws their pending proposal</li>
 *   <li>{@code /realty modify inbox} — tenant proposals awaiting you (as landlord)</li>
 *   <li>{@code /realty modify outbox} — your own pending proposals</li>
 * </ul>
 *
 * <p>A landlord's proposal applies automatically when the tenant next renews (the tenant declines by
 * not renewing). A tenant's proposal is a request the landlord must explicitly accept. Permission base:
 * {@code realty.command.modify.*} ({@code .others} grants the admin override).</p>
 */
public record ModifyCommandGroup(
        @NotNull RealtyPaperApi api,
        @NotNull ActorContexts actors,
        @NotNull ExecutorState executorState,
        @NotNull MessageContainer messages,
        @NotNull RealtyEventDispatch events,
        @NotNull PartyNames partyNames
) implements CustomCommandBean {

    @Override
    public @NotNull List<Command<? extends Source>> commands(@NotNull Command.Builder<Source> builder) {
        var base = builder.literal("modify");
        return List.of(
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
                        .build(),
                base.literal("inbox")
                        .permission("realty.command.modify.inbox")
                        .handler(this::executeInbox)
                        .build(),
                base.literal("outbox")
                        .permission("realty.command.modify.outbox")
                        .handler(this::executeOutbox)
                        .build()
        );
    }

    private void executeInbox(@NotNull CommandContext<Source> ctx) {
        if (!(ctx.sender().source() instanceof Player sender)) {
            ctx.sender().source().sendMessage(messages.messageFor(MessageKeys.COMMON_PLAYERS_ONLY));
            return;
        }
        // The inbox gathers the proposals of every landlord the player acts for: themself, and each
        // account or group they manage. Finding those asks Treasury and Vault, so it runs off the main thread.
        CompletableFuture.supplyAsync(() -> actors.forEveryParty(sender), executorState.dbExec())
                .thenCompose(actor -> api.listModificationsAwaitingLandlord(actor.manages()))
                .thenAccept(views -> {
                    if (views.isEmpty()) {
                        sender.sendMessage(messages.messageFor(MessageKeys.MODIFY_INBOX_NONE));
                        return;
                    }
                    Component output = messages.messageFor(MessageKeys.MODIFY_INBOX_HEADER);
                    for (LeaseholdModificationView view : views) {
                        output = output.appendNewline().append(messages.messageFor(MessageKeys.MODIFY_INBOX_ENTRY,
                                Placeholder.unparsed("region", view.worldGuardRegionId()),
                                Placeholder.unparsed("player", partyNames.display(view.proposerId())),
                                Placeholder.component("changes", describeChanges(view))));
                    }
                    sender.sendMessage(output);
                }).exceptionally(ex -> {
                    sender.sendMessage(messages.messageFor(MessageKeys.MODIFY_ERROR,
                            Placeholder.unparsed("error", String.valueOf(ex.getMessage()))));
                    return null;
                });
    }

    private void executeOutbox(@NotNull CommandContext<Source> ctx) {
        if (!(ctx.sender().source() instanceof Player sender)) {
            ctx.sender().source().sendMessage(messages.messageFor(MessageKeys.COMMON_PLAYERS_ONLY));
            return;
        }
        api.listPendingModificationsByProposer(sender.getUniqueId()).thenAccept(views -> {
            if (views.isEmpty()) {
                sender.sendMessage(messages.messageFor(MessageKeys.MODIFY_OUTBOX_NONE));
                return;
            }
            Component output = messages.messageFor(MessageKeys.MODIFY_OUTBOX_HEADER);
            for (LeaseholdModificationView view : views) {
                String statusKey = LeaseholdModificationStatus.ACTIVE.equals(view.status())
                        ? MessageKeys.MODIFY_STATUS_ACTIVE : MessageKeys.MODIFY_STATUS_AWAITING;
                output = output.appendNewline().append(messages.messageFor(MessageKeys.MODIFY_OUTBOX_ENTRY,
                        Placeholder.unparsed("region", view.worldGuardRegionId()),
                        Placeholder.component("changes", describeChanges(view)),
                        Placeholder.component("status", messages.messageFor(statusKey))));
            }
            sender.sendMessage(output);
        });
    }

    /** Renders the non-null proposed terms as a localized summary (formatting lives in messages.yml). */
    private @NotNull Component describeChanges(@NotNull LeaseholdModificationView view) {
        return LeaseholdChangeSummary.render(messages,
                view.newPrice(), view.newDurationSeconds(), view.newMaxExtensions());
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
        CompletableFuture.supplyAsync(() -> actors.forRegion(sender, bypass, region), executorState.dbExec())
                .thenComposeAsync(actor -> switch (action) {
                    case ACCEPT -> api.acceptModification(regionId, worldId, actor);
                    case REJECT -> api.rejectModification(regionId, worldId, actor);
                    case WITHDRAW -> api.withdrawModification(regionId, worldId, actor);
                }, executorState.mainThreadExec())
                .thenAccept(result -> {
            switch (result) {
                case RealtyBackend.ResolveModificationResult.Success success -> {
                    sender.sendMessage(messages.messageFor(action.successKey,
                            Placeholder.unparsed("region", regionId)));
                    events.fireSync(new LeaseModificationResolvedEvent(region, action.resolution,
                            success.proposerRole(), success.landlord(), success.tenantId()));
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
