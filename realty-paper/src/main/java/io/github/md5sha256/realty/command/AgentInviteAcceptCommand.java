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
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Handles {@code /realty agent invite accept <region>}.
 *
 * <p>Accepts a pending agent invite, adding the player as a sanctioned auctioneer.</p>
 *
 * <p>Permission: {@code realty.command.agent.invite.accept}.</p>
 */
public record AgentInviteAcceptCommand(@NotNull ExecutorState executorState,
                                        @NotNull RealtyLogicImpl logic,
                                        @NotNull NotificationService notificationService,
                                        @NotNull MessageContainer messages) implements CustomCommandBean.Single {

    @Override
    public @NotNull Command<CommandSourceStack> command(@NotNull Command.Builder<CommandSourceStack> builder) {
        return builder
                .literal("agent")
                .literal("invite")
                .literal("accept")
                .permission("realty.command.agent.invite.accept")
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
                RealtyLogicImpl.AcceptAgentInviteResult result = logic.acceptAgentInvite(regionId, worldId, inviteeId);
                switch (result) {
                    case RealtyLogicImpl.AcceptAgentInviteResult.Success(UUID inviterId) -> {
                        sender.sendMessage(messages.messageFor(MessageKeys.AGENT_INVITE_ACCEPT_SUCCESS,
                                Placeholder.unparsed("region", regionId)));
                        notificationService.queueNotification(inviterId,
                                messages.messageFor(MessageKeys.NOTIFICATION_AGENT_INVITE_ACCEPTED,
                                        Placeholder.unparsed("player", player.getName()),
                                        Placeholder.unparsed("region", regionId)));
                    }
                    case RealtyLogicImpl.AcceptAgentInviteResult.NotFound() ->
                            sender.sendMessage(messages.messageFor(MessageKeys.AGENT_INVITE_ACCEPT_NOT_FOUND,
                                    Placeholder.unparsed("region", regionId)));
                }
            } catch (Exception ex) {
                sender.sendMessage(messages.messageFor(MessageKeys.AGENT_INVITE_ACCEPT_ERROR,
                        Placeholder.unparsed("error", ex.getMessage())));
            }
        }, executorState.dbExec());
    }
}
