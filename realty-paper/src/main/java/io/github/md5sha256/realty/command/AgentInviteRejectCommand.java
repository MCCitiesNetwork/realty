package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.api.NotificationService;
import io.github.md5sha256.realty.command.util.WorldGuardRegion;
import io.github.md5sha256.realty.command.util.WorldGuardRegionParser;
import io.github.md5sha256.realty.database.RealtyLogicImpl;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.localisation.MessageKeys;
import io.github.md5sha256.realty.util.ExecutorState;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Handles {@code /realty agent invite reject <region>}.
 *
 * <p>Rejects a pending agent invite, removing the invite without adding the player
 * as a sanctioned auctioneer.</p>
 *
 * <p>Permission: {@code realty.command.agent.invite.reject}.</p>
 */
public record AgentInviteRejectCommand(@NotNull ExecutorState executorState,
                                        @NotNull RealtyLogicImpl logic,
                                        @NotNull NotificationService notificationService,
                                        @NotNull MessageContainer messages) implements CustomCommandBean.Single {

    @Override
    public @NotNull Command<CommandSourceStack> command(@NotNull Command.Builder<CommandSourceStack> builder) {
        return builder
                .literal("agent")
                .literal("invite")
                .literal("reject")
                .permission("realty.command.agent.invite.reject")
                .required("region", WorldGuardRegionParser.worldGuardRegion())
                .handler(this::execute)
                .build();
    }

    private void execute(@NotNull CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.sender().getSender();
        if (!(sender instanceof Player player)) {
            return;
        }
        WorldGuardRegion region = ctx.get("region");
        String regionId = region.region().getId();
        UUID worldId = region.world().getUID();
        UUID inviteeId = player.getUniqueId();
        CompletableFuture.runAsync(() -> {
            try {
                RealtyLogicImpl.RejectAgentInviteResult result = logic.rejectAgentInvite(regionId, worldId, inviteeId);
                switch (result) {
                    case RealtyLogicImpl.RejectAgentInviteResult.Success(UUID inviterId) -> {
                        sender.sendMessage(messages.messageFor(MessageKeys.AGENT_INVITE_REJECT_SUCCESS,
                                Placeholder.unparsed("region", regionId)));
                        notificationService.queueNotification(inviterId,
                                messages.messageFor(MessageKeys.NOTIFICATION_AGENT_INVITE_REJECTED,
                                        Placeholder.unparsed("player", player.getName()),
                                        Placeholder.unparsed("region", regionId)));
                    }
                    case RealtyLogicImpl.RejectAgentInviteResult.NotFound() ->
                            sender.sendMessage(messages.messageFor(MessageKeys.AGENT_INVITE_REJECT_NOT_FOUND,
                                    Placeholder.unparsed("region", regionId)));
                }
            } catch (Exception ex) {
                sender.sendMessage(messages.messageFor(MessageKeys.AGENT_INVITE_REJECT_ERROR,
                        Placeholder.unparsed("error", ex.getMessage())));
            }
        }, executorState.dbExec());
    }
}
