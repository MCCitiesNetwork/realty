package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.api.DateTimeFormatters;
import io.github.md5sha256.realty.api.ExecutorState;
import io.github.md5sha256.realty.api.RealtyPaperApi;
import io.github.md5sha256.realty.api.RealtyPaperApi.CancelSubregionDeleteResult;
import io.github.md5sha256.realty.api.RealtyPaperApi.DeleteSubregionResult;
import io.github.md5sha256.realty.api.WorldGuardRegion;
import io.github.md5sha256.realty.api.event.RealtyNotificationEvent;
import io.github.md5sha256.realty.api.event.RegionDeleteEvent;
import io.github.md5sha256.realty.api.event.RegionDeletedEvent;
import io.github.md5sha256.realty.auth.ActorContexts;
import io.github.md5sha256.realty.command.util.WorldGuardRegionParser;
import io.github.md5sha256.realty.event.RealtyEventDispatch;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.localisation.MessageKeys;
import io.github.md5sha256.realty.wand.SubregionWand;
import io.github.md5sha256.realty.wand.SubregionWandManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.paper.util.sender.Source;
import org.incendo.cloud.parser.flag.CommandFlag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.LongSupplier;

/**
 * Registers the player-facing subregion commands:
 * <ul>
 *     <li>{@code /realty subregion wand} — hands out the selection wand.</li>
 *     <li>{@code /realty subregion clear} — discards the current selection.</li>
 *     <li>{@code /realty subregion confirm} — opens the height dialog for the marked footprint,
 *     then the creation dialog.</li>
 *     <li>{@code /realty subregion delete <region> [--confirm]} — deletes a subregion at once while
 *     vacant, or, while rented, when its lease ends.</li>
 *     <li>{@code /realty subregion delete cancel <region>} — cancels a scheduled delete.</li>
 * </ul>
 *
 * <p>Selection geometry, parent auto-detection and validation live in
 * {@link SubregionDialog} and {@code SubregionSelectionValidator}. Deleting needs
 * {@code realty.command.subregion.delete}; {@code realty.command.subregion.delete.others} lets the
 * player act for a landlord they do not manage.</p>
 */
public record SubregionCommandGroup(
        @NotNull SubregionWand wand,
        @NotNull SubregionWandManager wandManager,
        @NotNull SubregionDialog dialog,
        @NotNull MessageContainer messages,
        @NotNull RealtyPaperApi api,
        @NotNull ActorContexts actors,
        @NotNull ExecutorState executorState,
        @NotNull RealtyEventDispatch events,
        @NotNull LongSupplier terminationNoticeSeconds
) implements CustomCommandBean {

    private static final String DELETE_PERMISSION = "realty.command.subregion.delete";
    private static final String DELETE_OTHERS_PERMISSION = "realty.command.subregion.delete.others";

    /** Confirms a delete the player was shown. */
    private static final CommandFlag<Void> CONFIRM_FLAG = CommandFlag.<Source>builder("confirm").build();

    @Override
    public @NotNull List<Command<? extends Source>> commands(
            @NotNull Command.Builder<Source> builder) {
        var base = builder.literal("subregion");
        return List.of(
                base.literal("wand")
                        .permission("realty.command.subregion.wand")
                        .handler(this::executeWand)
                        .build(),
                base.literal("clear")
                        .permission("realty.command.subregion.wand")
                        .handler(this::executeClear)
                        .build(),
                base.literal("confirm")
                        .permission("realty.command.subregion.confirm")
                        .handler(this::executeConfirm)
                        .build(),
                base.literal("delete")
                        .permission(DELETE_PERMISSION)
                        .required("region", WorldGuardRegionParser.worldGuardRegion())
                        .flag(CONFIRM_FLAG)
                        .handler(this::executeDelete)
                        .build(),
                base.literal("delete")
                        .literal("cancel")
                        .permission(DELETE_PERMISSION)
                        .required("region", WorldGuardRegionParser.worldGuardRegion())
                        .handler(this::executeCancelDelete)
                        .build()
        );
    }

    private void executeWand(@NotNull CommandContext<Source> ctx) {
        if (!(ctx.sender().source() instanceof Player player)) {
            ctx.sender().source().sendMessage(messages.messageFor(MessageKeys.COMMON_PLAYERS_ONLY));
            return;
        }
        ItemStack item = wand.createWand();
        player.getInventory().addItem(item).forEach((index, leftover) ->
                player.getWorld().dropItem(player.getLocation(), leftover));
        player.sendMessage(messages.messageFor(MessageKeys.SUBREGION_WAND_GIVEN));
    }

    private void executeClear(@NotNull CommandContext<Source> ctx) {
        if (!(ctx.sender().source() instanceof Player player)) {
            ctx.sender().source().sendMessage(messages.messageFor(MessageKeys.COMMON_PLAYERS_ONLY));
            return;
        }
        if (wandManager.get(player.getUniqueId()) == null) {
            player.sendMessage(messages.messageFor(MessageKeys.SUBREGION_NOTHING_TO_CLEAR));
            return;
        }
        wandManager.clear(player.getUniqueId());
        player.sendMessage(messages.messageFor(MessageKeys.SUBREGION_SELECTION_CLEARED));
    }

    private void executeConfirm(@NotNull CommandContext<Source> ctx) {
        if (!(ctx.sender().source() instanceof Player player)) {
            ctx.sender().source().sendMessage(messages.messageFor(MessageKeys.COMMON_PLAYERS_ONLY));
            return;
        }
        dialog.openHeight(player);
    }

    // --- Delete ---

    private void executeDelete(@NotNull CommandContext<Source> ctx) {
        if (!(ctx.sender().source() instanceof Player player)) {
            ctx.sender().source().sendMessage(messages.messageFor(MessageKeys.COMMON_PLAYERS_ONLY));
            return;
        }
        WorldGuardRegion region = ctx.get("region");
        if (!ctx.flags().hasFlag(CONFIRM_FLAG)) {
            showDeletePrompt(player, region);
            return;
        }
        if (!events.fireSync(new RegionDeleteEvent(region, player.getUniqueId()))) {
            player.sendMessage(messages.messageFor(MessageKeys.COMMON_ACTION_CANCELLED));
            return;
        }
        boolean bypass = player.hasPermission(DELETE_OTHERS_PERMISSION);
        CompletableFuture.supplyAsync(() -> actors.forRegion(player, bypass, region), executorState.dbExec())
                .thenComposeAsync(actor -> api.deleteSubregion(region, actor), executorState.mainThreadExec())
                .thenAcceptAsync(result -> reportDelete(player, region, result), executorState.mainThreadExec())
                .exceptionally(ex -> reportError(player, ex));
    }

    /** Says what a confirmed delete would do, with a link that confirms it. Nothing is changed. */
    private void showDeletePrompt(@NotNull Player player, @NotNull WorldGuardRegion region) {
        String regionId = region.region().getId();
        var parent = region.region().getParent();
        if (parent == null) {
            player.sendMessage(messages.messageFor(MessageKeys.SUBREGION_DELETE_NOT_SUBREGION,
                    Placeholder.unparsed("region", regionId)));
            return;
        }
        String command = "/realty subregion delete " + regionId + " --confirm";
        UUID worldId = region.world().getUID();
        api.getFreeholdContract(parent.getId(), worldId)
                .thenCombine(api.getLeaseholdContract(regionId, worldId), (freehold, lease) -> freehold == null ? null : lease)
                .thenAcceptAsync(lease -> {
            if (lease == null) {
                player.sendMessage(regionMessage(MessageKeys.SUBREGION_DELETE_NOT_SUBREGION, regionId));
                return;
            }
            if (lease.tenantId() == null) {
                player.sendMessage(messages.messageFor(MessageKeys.SUBREGION_DELETE_CONFIRM_VACANT,
                        Placeholder.unparsed("region", regionId),
                        Placeholder.unparsed("parent", parent.getId())));
            } else {
                // A landlord's termination ends the lease after the notice period.
                LocalDateTime ends = lease.terminationEffectiveDate() != null
                        ? lease.terminationEffectiveDate()
                        : LocalDateTime.now().plusSeconds(terminationNoticeSeconds.getAsLong());
                player.sendMessage(messages.messageFor(MessageKeys.SUBREGION_DELETE_CONFIRM_RENTED,
                        Placeholder.unparsed("region", regionId),
                        Placeholder.unparsed("date", format(ends))));
            }
            player.sendMessage(messages.commandLink(MessageKeys.SUBREGION_DELETE_CONFIRM_LINK, command));
        }, executorState.mainThreadExec());
    }

    private void reportDelete(@NotNull Player player, @NotNull WorldGuardRegion region,
                              @NotNull DeleteSubregionResult result) {
        String regionId = region.region().getId();
        switch (result) {
            case DeleteSubregionResult.Deleted ignored -> {
                player.sendMessage(regionMessage(MessageKeys.SUBREGION_DELETE_SUCCESS, regionId));
                events.fireSync(new RegionDeletedEvent(region, player.getUniqueId()));
            }
            case DeleteSubregionResult.Scheduled scheduled -> {
                player.sendMessage(messages.messageFor(scheduled.termination() != null
                                ? MessageKeys.SUBREGION_DELETE_SCHEDULED
                                : MessageKeys.SUBREGION_DELETE_SCHEDULED_EXISTING,
                        Placeholder.unparsed("region", regionId),
                        Placeholder.unparsed("date", format(scheduled.effectiveDate()))));
                if (scheduled.termination() != null) {
                    events.fireSync(TerminateCommand.scheduledEvent(region, scheduled.termination()));
                }
                tellTenantOfRemoval(region, scheduled);
            }
            case DeleteSubregionResult.AlreadyScheduled already ->
                    player.sendMessage(messages.messageFor(MessageKeys.SUBREGION_DELETE_ALREADY_SCHEDULED,
                            Placeholder.unparsed("region", regionId),
                            Placeholder.unparsed("date", format(already.effectiveDate()))));
            case DeleteSubregionResult.TerminatedNotMarked notMarked -> {
                player.sendMessage(messages.messageFor(MessageKeys.SUBREGION_DELETE_TERMINATED_NOT_MARKED,
                        Placeholder.unparsed("region", regionId),
                        Placeholder.unparsed("date", format(notMarked.termination().effectiveDate()))));
                events.fireSync(TerminateCommand.scheduledEvent(region, notMarked.termination()));
            }
            case DeleteSubregionResult.TerminationFailed failed ->
                    player.sendMessage(TerminateCommand.failureMessage(messages, failed.reason(), regionId));
            case DeleteSubregionResult.NotSubregion ignored ->
                    player.sendMessage(regionMessage(MessageKeys.SUBREGION_DELETE_NOT_SUBREGION, regionId));
            case DeleteSubregionResult.NotAuthorized ignored ->
                    player.sendMessage(regionMessage(MessageKeys.SUBREGION_DELETE_NOT_AUTHORIZED, regionId));
            case DeleteSubregionResult.WorldGuardSaveError saveError ->
                    player.sendMessage(messages.messageFor(MessageKeys.SUBREGION_DELETE_WORLDGUARD_SAVE_ERROR,
                            Placeholder.unparsed("region", regionId),
                            Placeholder.unparsed("error", saveError.error())));
            case DeleteSubregionResult.Error error ->
                    player.sendMessage(messages.messageFor(MessageKeys.SUBREGION_DELETE_ERROR,
                            Placeholder.unparsed("error", error.message())));
        }
    }

    /** Tells the tenant the plot goes when their lease ends. */
    private void tellTenantOfRemoval(@NotNull WorldGuardRegion region, @NotNull DeleteSubregionResult.Scheduled scheduled) {
        String regionId = region.region().getId();
        events.fireSync(new RealtyNotificationEvent(List.of(scheduled.tenantId()),
                MessageKeys.NOTIFICATION_TERMINATION_SUBREGION_REMOVED,
                messages.messageFor(MessageKeys.NOTIFICATION_TERMINATION_SUBREGION_REMOVED,
                        Placeholder.unparsed("region", regionId),
                        Placeholder.unparsed("date", format(scheduled.effectiveDate()))),
                region));
    }

    private void executeCancelDelete(@NotNull CommandContext<Source> ctx) {
        if (!(ctx.sender().source() instanceof Player player)) {
            ctx.sender().source().sendMessage(messages.messageFor(MessageKeys.COMMON_PLAYERS_ONLY));
            return;
        }
        WorldGuardRegion region = ctx.get("region");
        String regionId = region.region().getId();
        boolean bypass = player.hasPermission(DELETE_OTHERS_PERMISSION);
        CompletableFuture.supplyAsync(() -> actors.forRegion(player, bypass, region), executorState.dbExec())
                .thenComposeAsync(actor -> api.cancelSubregionDelete(region, actor), executorState.mainThreadExec())
                .thenAcceptAsync(result -> {
                    switch (result) {
                        case CancelSubregionDeleteResult.Success ignored -> {
                            player.sendMessage(regionMessage(MessageKeys.SUBREGION_DELETE_CANCELLED, regionId));
                            player.sendMessage(messages.commandLink(
                                    MessageKeys.SUBREGION_DELETE_CANCEL_TERMINATION_LINK,
                                    "/realty terminate cancel " + regionId));
                        }
                        case CancelSubregionDeleteResult.NotScheduled ignored ->
                                player.sendMessage(regionMessage(MessageKeys.SUBREGION_DELETE_NOT_SCHEDULED, regionId));
                        case CancelSubregionDeleteResult.NotAuthorized ignored ->
                                player.sendMessage(regionMessage(MessageKeys.SUBREGION_DELETE_NOT_AUTHORIZED, regionId));
                        case CancelSubregionDeleteResult.NotSubregion ignored ->
                                player.sendMessage(regionMessage(MessageKeys.SUBREGION_DELETE_NOT_SUBREGION, regionId));
                    }
                }, executorState.mainThreadExec())
                .exceptionally(ex -> reportError(player, ex));
    }

    private static @NotNull String format(@NotNull LocalDateTime dateTime) {
        return dateTime.format(DateTimeFormatters.DATE_TIME);
    }

    private @NotNull Component regionMessage(@NotNull String key, @NotNull String regionId) {
        return messages.messageFor(key, Placeholder.unparsed("region", regionId));
    }

    private @Nullable Void reportError(@NotNull Player player, @NotNull Throwable ex) {
        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
        player.sendMessage(messages.messageFor(MessageKeys.SUBREGION_DELETE_ERROR,
                Placeholder.unparsed("error", String.valueOf(cause.getMessage()))));
        return null;
    }
}
