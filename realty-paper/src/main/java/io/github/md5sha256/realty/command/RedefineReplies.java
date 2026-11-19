package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.api.RealtyPaperApi.RedefineRegionResult;
import io.github.md5sha256.realty.api.event.RealtyNotificationEvent;
import io.github.md5sha256.realty.event.RealtyEventDispatch;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.localisation.MessageKeys;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Replies to a redefine and tells the region's tenant or title holder. Both redefine commands, the
 * wand and the WorldEdit one, report through this. Call it on the main thread.
 */
public record RedefineReplies(@NotNull MessageContainer messages, @NotNull RealtyEventDispatch events) {

    /**
     * @param actorId  the player who redefined the region; empty for the console. Nobody is told
     *                 of their own redefine.
     * @param regionId the region's id, for the refusals that name it
     */
    public void report(@NotNull CommandSender sender, @NotNull Optional<UUID> actorId,
                       @NotNull String regionId, @NotNull RedefineRegionResult result) {
        switch (result) {
            case RedefineRegionResult.Redefined redefined -> reportRedefined(sender, actorId, redefined);
            case RedefineRegionResult.WorldGuardSaveError saveError -> {
                reportRedefined(sender, actorId, saveError.redefined());
                sender.sendMessage(this.messages.messageFor(MessageKeys.REDEFINE_WORLDGUARD_SAVE_ERROR,
                        Placeholder.unparsed("region", regionId),
                        Placeholder.unparsed("error", saveError.error())));
            }
            case RedefineRegionResult.UnsupportedSelection ignored ->
                    sender.sendMessage(this.messages.messageFor(MessageKeys.REDEFINE_UNSUPPORTED_SELECTION));
            case RedefineRegionResult.WrongWorld ignored -> reply(sender, MessageKeys.REDEFINE_WRONG_WORLD, regionId);
            case RedefineRegionResult.SameShape ignored -> reply(sender, MessageKeys.REDEFINE_SAME_SHAPE, regionId);
            case RedefineRegionResult.TooSmall tooSmall ->
                    sender.sendMessage(this.messages.messageFor(MessageKeys.REDEFINE_TOO_SMALL,
                            Placeholder.unparsed("volume", Long.toString(tooSmall.volume())),
                            Placeholder.unparsed("min-volume", Integer.toString(tooSmall.minVolume()))));
            case RedefineRegionResult.OutsideParent outside ->
                    sender.sendMessage(this.messages.messageFor(MessageKeys.REDEFINE_OUTSIDE_PARENT,
                            Placeholder.unparsed("region", regionId),
                            Placeholder.unparsed("parent", outside.parentId())));
            case RedefineRegionResult.OverlapsSibling overlap ->
                    sender.sendMessage(this.messages.messageFor(MessageKeys.REDEFINE_OVERLAPS_SIBLING,
                            Placeholder.unparsed("region", regionId),
                            Placeholder.unparsed("sibling", overlap.siblingId())));
            case RedefineRegionResult.ChildOutside child ->
                    sender.sendMessage(this.messages.messageFor(MessageKeys.REDEFINE_CHILD_OUTSIDE,
                            Placeholder.unparsed("region", regionId),
                            Placeholder.unparsed("child", child.childId())));
            case RedefineRegionResult.NotRegistered ignored ->
                    reply(sender, MessageKeys.REDEFINE_NOT_REGISTERED, regionId);
            case RedefineRegionResult.NotAuthorized ignored ->
                    reply(sender, MessageKeys.REDEFINE_NOT_AUTHORIZED, regionId);
            case RedefineRegionResult.Occupied ignored -> reply(sender, MessageKeys.REDEFINE_OCCUPIED, regionId);
            case RedefineRegionResult.Cancelled ignored ->
                    sender.sendMessage(this.messages.messageFor(MessageKeys.COMMON_ACTION_CANCELLED));
            case RedefineRegionResult.UnsupportedRegion ignored ->
                    reply(sender, MessageKeys.REDEFINE_UNSUPPORTED_REGION, regionId);
            case RedefineRegionResult.CaptureRunning ignored ->
                    reply(sender, MessageKeys.REDEFINE_CAPTURE_RUNNING, regionId);
            case RedefineRegionResult.Error error -> error(sender, error.message());
        }
    }

    /** Replies to a redefine that failed with an exception. */
    public void error(@NotNull CommandSender sender, @NotNull Throwable error) {
        Throwable cause = error.getCause() != null ? error.getCause() : error;
        error(sender, String.valueOf(cause.getMessage()));
    }

    private void error(@NotNull CommandSender sender, @NotNull String message) {
        sender.sendMessage(this.messages.messageFor(MessageKeys.REDEFINE_ERROR, Placeholder.unparsed("error", message)));
    }

    private void reply(@NotNull CommandSender sender, @NotNull String key, @NotNull String regionId) {
        sender.sendMessage(this.messages.messageFor(key, Placeholder.unparsed("region", regionId)));
    }

    private void reportRedefined(@NotNull CommandSender sender, @NotNull Optional<UUID> actorId,
                                 @NotNull RedefineRegionResult.Redefined redefined) {
        String regionId = redefined.region().region().getId();
        sender.sendMessage(this.messages.messageFor(MessageKeys.REDEFINE_SUCCESS,
                Placeholder.unparsed("region", regionId),
                Placeholder.unparsed("old-volume", Long.toString(redefined.oldVolume())),
                Placeholder.unparsed("new-volume", Long.toString(redefined.newVolume()))));
        if (redefined.captureRemoved()) {
            reply(sender, MessageKeys.REDEFINE_CAPTURE_REMOVED, regionId);
        }
        redefined.occupantId().filter(occupant -> !actorId.equals(Optional.of(occupant))).ifPresent(occupant ->
                this.events.fireSync(new RealtyNotificationEvent(List.of(occupant),
                        MessageKeys.NOTIFICATION_REGION_REDEFINED,
                        this.messages.messageFor(MessageKeys.NOTIFICATION_REGION_REDEFINED,
                                Placeholder.unparsed("region", regionId)),
                        redefined.region())));
    }
}
