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
 * Handles {@code /realty agent invite <player> <region>}.
 *
 * <p>Invites a player as a sanctioned auctioneer for a region.
 * Only the title holder can send invites.</p>
 *
 * <p>Permission: {@code realty.command.agent.invite}.</p>
 */
public record AgentInviteCommand(@NotNull ExecutorState executorState,
                                  @NotNull RealtyLogicImpl logic,
                                  @NotNull NotificationService notificationService,
                                  @NotNull MessageContainer messages) implements CustomCommandBean.Single {

    @Override
    public @NotNull Command<CommandSourceStack> command(@NotNull Command.Builder<CommandSourceStack> builder) {
        return builder
                .literal("agent")
                .literal("invite")
                .permission("realty.command.agent.invite")
                .required("player", AuthorityParser.authority())
                .required("region", WorldGuardRegionParser.worldGuardRegion())
                .handler(this::execute)
                .build();
    }

    private void execute(@NotNull CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.sender().getSender();
        if (!(sender instanceof Player player)) {
            return;
        }
        UUID inviteeId = ctx.get("player");
        WorldGuardRegion region = ctx.get("region");
        String regionId = region.region().getId();
        UUID worldId = region.world().getUID();
        String inviteeName = resolveName(inviteeId);
        if (!region.region().getOwners().contains(player.getUniqueId())) {
            sender.sendMessage(messages.messageFor(MessageKeys.AGENT_INVITE_NOT_TITLEHOLDER,
                    Placeholder.unparsed("region", regionId)));
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                RealtyLogicImpl.InviteAgentResult result = logic.inviteAgent(regionId, worldId, player.getUniqueId(), inviteeId);
                switch (result) {
                    case RealtyLogicImpl.InviteAgentResult.Success() -> {
                        sender.sendMessage(messages.messageFor(MessageKeys.AGENT_INVITE_SUCCESS,
                                Placeholder.unparsed("player", inviteeName),
                                Placeholder.unparsed("region", regionId)));
                        notificationService.queueNotification(inviteeId,
                                messages.messageFor(MessageKeys.NOTIFICATION_AGENT_INVITED,
                                        Placeholder.unparsed("player", player.getName()),
                                        Placeholder.unparsed("region", regionId)));
                    }
                    case RealtyLogicImpl.InviteAgentResult.NoFreeholdContract() ->
                            sender.sendMessage(messages.messageFor(MessageKeys.AGENT_INVITE_NO_FREEHOLD,
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyLogicImpl.InviteAgentResult.IsTitleHolder() ->
                            sender.sendMessage(messages.messageFor(MessageKeys.AGENT_INVITE_IS_TITLEHOLDER,
                                    Placeholder.unparsed("player", inviteeName),
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyLogicImpl.InviteAgentResult.IsAuthority() ->
                            sender.sendMessage(messages.messageFor(MessageKeys.AGENT_INVITE_IS_AUTHORITY,
                                    Placeholder.unparsed("player", inviteeName),
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyLogicImpl.InviteAgentResult.AlreadyAgent() ->
                            sender.sendMessage(messages.messageFor(MessageKeys.AGENT_INVITE_ALREADY_AGENT,
                                    Placeholder.unparsed("player", inviteeName),
                                    Placeholder.unparsed("region", regionId)));
                    case RealtyLogicImpl.InviteAgentResult.AlreadyInvited() ->
                            sender.sendMessage(messages.messageFor(MessageKeys.AGENT_INVITE_ALREADY_INVITED,
                                    Placeholder.unparsed("player", inviteeName),
                                    Placeholder.unparsed("region", regionId)));
                }
            } catch (Exception ex) {
                sender.sendMessage(messages.messageFor(MessageKeys.AGENT_INVITE_ERROR,
                        Placeholder.unparsed("error", ex.getMessage())));
            }
        }, executorState.dbExec());
    }

    private static @NotNull String resolveName(@NotNull UUID uuid) {
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name != null ? name : uuid.toString();
    }
}
