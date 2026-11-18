package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.api.ActorContext;
import io.github.md5sha256.realty.api.ExecutorState;
import io.github.md5sha256.realty.api.RealtyPaperApi;
import io.github.md5sha256.realty.api.RealtyPaperApi.RenameRegionResult;
import io.github.md5sha256.realty.api.WorldGuardRegion;
import io.github.md5sha256.realty.api.event.RealtyNotificationEvent;
import io.github.md5sha256.realty.api.event.RegionRenameEvent;
import io.github.md5sha256.realty.api.event.RegionRenamedEvent;
import io.github.md5sha256.realty.auth.ActorContexts;
import io.github.md5sha256.realty.command.util.WorldGuardRegionParser;
import io.github.md5sha256.realty.event.RealtyEventDispatch;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.localisation.MessageKeys;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.paper.util.sender.Source;
import org.incendo.cloud.parser.standard.StringParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Handles {@code /realty rename <region> <name>}.
 *
 * <p>{@code realty.command.rename} lets a player rename a subregion whose landlord they manage.
 * {@code realty.command.rename.others} lets them rename any other registered region. The console
 * may rename any registered region.</p>
 */
public record RenameCommand(
        @NotNull RealtyPaperApi api,
        @NotNull ActorContexts actors,
        @NotNull ExecutorState executorState,
        @NotNull MessageContainer messages,
        @NotNull RealtyEventDispatch events
) implements CustomCommandBean.Single {

    private static final String PERMISSION = "realty.command.rename";
    private static final String OTHERS_PERMISSION = "realty.command.rename.others";

    @Override
    public @NotNull Command<? extends Source> command(@NotNull Command.Builder<Source> builder) {
        return builder
                .literal("rename")
                .permission(PERMISSION)
                .required("region", WorldGuardRegionParser.worldGuardRegion())
                .required("name", StringParser.stringParser())
                .handler(this::execute)
                .build();
    }

    private void execute(@NotNull CommandContext<Source> ctx) {
        CommandSender sender = ctx.sender().source();
        WorldGuardRegion region = ctx.get("region");
        String newName = ctx.get("name");
        // The console has no player id; the events then carry no actor.
        UUID actorId = sender instanceof Player player ? player.getUniqueId() : null;
        if (!events.fireSync(new RegionRenameEvent(region, actorId, newName))) {
            sender.sendMessage(messages.messageFor(MessageKeys.COMMON_ACTION_CANCELLED));
            return;
        }
        actorFor(sender, region)
                .thenComposeAsync(actor -> api.renameRegion(region, newName, actor), executorState.mainThreadExec())
                .thenAcceptAsync(result -> report(sender, actorId, region, newName, result),
                        executorState.mainThreadExec())
                .exceptionally(ex -> {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    reply(sender, MessageKeys.RENAME_ERROR, "error", String.valueOf(cause.getMessage()));
                    return null;
                });
    }

    /** Reads which parties a player acts for, off the main thread. The console acts for all. */
    private @NotNull CompletableFuture<ActorContext> actorFor(@NotNull CommandSender sender,
                                                              @NotNull WorldGuardRegion region) {
        if (!(sender instanceof Player player)) {
            return CompletableFuture.completedFuture(ActorContext.console());
        }
        boolean bypass = player.hasPermission(OTHERS_PERMISSION);
        return CompletableFuture.supplyAsync(() -> actors.forRegion(player, bypass, region), executorState.dbExec());
    }

    private void report(@NotNull CommandSender sender, @Nullable UUID actorId, @NotNull WorldGuardRegion region,
                        @NotNull String newName, @NotNull RenameRegionResult result) {
        String regionId = region.region().getId();
        switch (result) {
            case RenameRegionResult.Renamed renamed -> reportRenamed(sender, actorId, renamed);
            case RenameRegionResult.WorldGuardSaveError saveError -> {
                reportRenamed(sender, actorId, saveError.renamed());
                sender.sendMessage(messages.messageFor(MessageKeys.RENAME_WORLDGUARD_SAVE_ERROR,
                        Placeholder.unparsed("region", saveError.renamed().region().region().getId()),
                        Placeholder.unparsed("error", saveError.error())));
            }
            case RenameRegionResult.InvalidName ignored ->
                    reply(sender, MessageKeys.RENAME_INVALID_NAME, "name", newName);
            case RenameRegionResult.SameName ignored -> reply(sender, MessageKeys.RENAME_SAME_NAME, "region", regionId);
            case RenameRegionResult.NameTaken ignored -> reply(sender, MessageKeys.RENAME_NAME_TAKEN, "name", newName);
            case RenameRegionResult.NotRegistered ignored ->
                    reply(sender, MessageKeys.RENAME_NOT_REGISTERED, "region", regionId);
            case RenameRegionResult.NotAuthorized ignored ->
                    reply(sender, MessageKeys.RENAME_NOT_AUTHORIZED, "region", regionId);
            case RenameRegionResult.UnsupportedRegion ignored ->
                    reply(sender, MessageKeys.RENAME_UNSUPPORTED_REGION, "region", regionId);
            case RenameRegionResult.CaptureRunning ignored ->
                    reply(sender, MessageKeys.RENAME_CAPTURE_RUNNING, "region", regionId);
            case RenameRegionResult.Error error -> reply(sender, MessageKeys.RENAME_ERROR, "error", error.message());
        }
    }

    /** Sends a message that has one placeholder. */
    private void reply(@NotNull CommandSender sender, @NotNull String key,
                       @NotNull String placeholder, @NotNull String value) {
        sender.sendMessage(messages.messageFor(key, Placeholder.unparsed(placeholder, value)));
    }

    /** Replies, tells other plugins, and tells the tenant. */
    private void reportRenamed(@NotNull CommandSender sender, @Nullable UUID actorId,
                               @NotNull RenameRegionResult.Renamed renamed) {
        String newId = renamed.region().region().getId();
        sender.sendMessage(messages.messageFor(MessageKeys.RENAME_SUCCESS,
                Placeholder.unparsed("old", renamed.oldName()),
                Placeholder.unparsed("region", newId)));
        if (renamed.leftGroupedProfile()) {
            reply(sender, MessageKeys.RENAME_LEFT_GROUPED_PROFILE, "old", renamed.oldName());
        }
        events.fireSync(new RegionRenamedEvent(renamed.region(), actorId, renamed.oldName()));
        UUID tenantId = renamed.tenantId();
        if (tenantId != null && !tenantId.equals(actorId)) {
            events.fireSync(new RealtyNotificationEvent(List.of(tenantId),
                    MessageKeys.NOTIFICATION_REGION_RENAMED,
                    messages.messageFor(MessageKeys.NOTIFICATION_REGION_RENAMED,
                            Placeholder.unparsed("old", renamed.oldName()),
                            Placeholder.unparsed("region", newId)),
                    renamed.region()));
        }
    }
}
