package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.api.NotificationService;
import io.github.md5sha256.realty.api.RealtyBackend;
import io.github.md5sha256.realty.api.RealtyPaperApi;
import io.github.md5sha256.realty.command.util.AuthorityParser;
import io.github.md5sha256.realty.command.util.WorldGuardRegion;
import io.github.md5sha256.realty.command.util.WorldGuardRegionResolver;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.localisation.MessageKeys;
import org.incendo.cloud.paper.util.sender.Source;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Handles {@code /realty agent invite withdraw <player> <region>}.
 *
 * <p>Withdraws a pending agent invite that was previously sent to a player.</p>
 *
 * <p>Permission: {@code realty.command.agent.invite.withdraw}.</p>
 */
public record AgentInviteWithdrawCommand(@NotNull RealtyPaperApi api,
                                          @NotNull NotificationService notificationService,
                                          @NotNull MessageContainer messages) implements CustomCommandBean.Single {

    @Override
    public @NotNull Command<? extends Source> command(@NotNull Command.Builder<Source> builder) {
        return builder
                .literal("agent")
                .literal("invite")
                .literal("withdraw")
                .permission("realty.command.agent.invite.withdraw")
                .required("player", AuthorityParser.authority())
                .optional("region", WorldGuardRegionResolver.worldGuardRegionResolver())
                .handler(this::execute)
                .build();
    }

    private void execute(@NotNull CommandContext<Source> ctx) {
        CommandSender sender = ctx.sender().source();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.messageFor(MessageKeys.COMMON_PLAYERS_ONLY));
            return;
        }
        UUID inviteeId = ctx.get("player");
        WorldGuardRegion region = ctx.<WorldGuardRegion>optional("region")
                .orElseGet(() -> WorldGuardRegionResolver.resolveAtLocation(player.getLocation()));
        if (region == null) {
            player.sendMessage(messages.messageFor(MessageKeys.ERROR_NO_REGION));
            return;
        }
        String regionId = region.region().getId();
        UUID worldId = region.world().getUID();
        String inviteeName = resolveName(inviteeId);
        if (!region.region().getOwners().contains(player.getUniqueId())) {
            sender.sendMessage(messages.messageFor(MessageKeys.AGENT_INVITE_WITHDRAW_NOT_FOUND,
                    Placeholder.unparsed("player", inviteeName),
                    Placeholder.unparsed("region", regionId)));
            return;
        }
        api.withdrawAgentInvite(regionId, worldId, inviteeId).thenAccept(result -> {
            switch (result) {
                case RealtyBackend.WithdrawAgentInviteResult.Success() -> {
                    sender.sendMessage(messages.messageFor(MessageKeys.AGENT_INVITE_WITHDRAW_SUCCESS,
                            Placeholder.unparsed("player", inviteeName),
                            Placeholder.unparsed("region", regionId)));
                    notificationService.queueNotification(inviteeId,
                            messages.messageFor(MessageKeys.NOTIFICATION_AGENT_INVITE_WITHDRAWN,
                                    Placeholder.unparsed("player", resolveName(player.getUniqueId())),
                                    Placeholder.unparsed("region", regionId)));
                }
                case RealtyBackend.WithdrawAgentInviteResult.NotFound() ->
                        sender.sendMessage(messages.messageFor(MessageKeys.AGENT_INVITE_WITHDRAW_NOT_FOUND,
                                Placeholder.unparsed("player", inviteeName),
                                Placeholder.unparsed("region", regionId)));
            }
        }).exceptionally(ex -> {
            sender.sendMessage(messages.messageFor(MessageKeys.AGENT_INVITE_WITHDRAW_ERROR,
                    Placeholder.unparsed("error", ex.getMessage())));
            return null;
        });
    }

    private static @NotNull String resolveName(@NotNull UUID uuid) {
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name != null ? name : uuid.toString();
    }
}
