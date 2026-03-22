package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.api.NotificationService;
import io.github.md5sha256.realty.command.util.AuthorityParser;
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
 * Handles {@code /realty agent invite withdraw <player> <region>}.
 *
 * <p>Withdraws a pending agent invite that was previously sent to a player.</p>
 *
 * <p>Permission: {@code realty.command.agent.invite.withdraw}.</p>
 */
public record AgentInviteWithdrawCommand(@NotNull ExecutorState executorState,
                                          @NotNull RealtyLogicImpl logic,
                                          @NotNull NotificationService notificationService,
                                          @NotNull MessageContainer messages) implements CustomCommandBean.Single {

    @Override
    public @NotNull Command<CommandSourceStack> command(@NotNull Command.Builder<CommandSourceStack> builder) {
        return builder
                .literal("agent")
                .literal("invite")
                .literal("withdraw")
                .permission("realty.command.agent.invite.withdraw")
                .required("player", AuthorityParser.authority())
                .required("region", WorldGuardRegionParser.worldGuardRegion())
                .handler(this::execute)
                .build();
    }

    private void execute(@NotNull CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.sender().getSender();
        if (!(sender instanceof Player)) {
            return;
        }
        UUID inviteeId = ctx.get("player");
        WorldGuardRegion region = ctx.get("region");
        String regionId = region.region().getId();
        UUID worldId = region.world().getUID();
        String inviteeName = resolveName(inviteeId);
        CompletableFuture.runAsync(() -> {
            try {
                RealtyLogicImpl.WithdrawAgentInviteResult result = logic.withdrawAgentInvite(regionId, worldId, inviteeId);
                switch (result) {
                    case RealtyLogicImpl.WithdrawAgentInviteResult.Success() -> {
                        sender.sendMessage(messages.messageFor(MessageKeys.AGENT_INVITE_WITHDRAW_SUCCESS,
                                Placeholder.unparsed("player", inviteeName),
                                Placeholder.unparsed("region", regionId)));
                        notificationService.queueNotification(inviteeId,
                                messages.messageFor(MessageKeys.NOTIFICATION_AGENT_INVITE_WITHDRAWN,
                                        Placeholder.unparsed("player", resolveName(((Player) sender).getUniqueId())),
                                        Placeholder.unparsed("region", regionId)));
                    }
                    case RealtyLogicImpl.WithdrawAgentInviteResult.NotFound() ->
                            sender.sendMessage(messages.messageFor(MessageKeys.AGENT_INVITE_WITHDRAW_NOT_FOUND,
                                    Placeholder.unparsed("player", inviteeName),
                                    Placeholder.unparsed("region", regionId)));
                }
            } catch (Exception ex) {
                sender.sendMessage(messages.messageFor(MessageKeys.AGENT_INVITE_WITHDRAW_ERROR,
                        Placeholder.unparsed("error", ex.getMessage())));
            }
        }, executorState.dbExec());
    }

    private static @NotNull String resolveName(@NotNull UUID uuid) {
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name != null ? name : uuid.toString();
    }
}
