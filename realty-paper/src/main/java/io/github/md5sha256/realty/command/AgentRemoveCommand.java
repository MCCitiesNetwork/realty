package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.api.NotificationService;
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
 * Handles {@code /realty agent remove <player> <region>}.
 *
 * <p>Removes a player from the sanctioned auctioneers list for a region.</p>
 *
 * <p>Permission: {@code realty.command.agent.remove}.</p>
 */
public record AgentRemoveCommand(@NotNull RealtyPaperApi api,
                                  @NotNull NotificationService notificationService,
                                  @NotNull MessageContainer messages) implements CustomCommandBean.Single {

    @Override
    public @NotNull Command<? extends Source> command(@NotNull Command.Builder<Source> builder) {
        return builder
                .literal("agent")
                .literal("remove")
                .permission("realty.command.agent.remove")
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
        UUID targetId = ctx.get("player");
        WorldGuardRegion region = ctx.<WorldGuardRegion>optional("region")
                .orElseGet(() -> WorldGuardRegionResolver.resolveAtLocation(player.getLocation()));
        if (region == null) {
            player.sendMessage(messages.messageFor(MessageKeys.ERROR_NO_REGION));
            return;
        }
        String regionId = region.region().getId();
        UUID worldId = region.world().getUID();
        String targetName = resolveName(targetId);
        if (!region.region().getOwners().contains(player.getUniqueId())) {
            sender.sendMessage(messages.messageFor(MessageKeys.AGENT_REMOVE_NOT_FOUND,
                    Placeholder.unparsed("player", targetName),
                    Placeholder.unparsed("region", regionId)));
            return;
        }
        UUID actorId = player.getUniqueId();
        api.removeSanctionedAuctioneer(regionId, worldId, targetId, actorId).thenAccept(rows -> {
            if (rows > 0) {
                sender.sendMessage(messages.messageFor(MessageKeys.AGENT_REMOVE_SUCCESS,
                        Placeholder.unparsed("player", targetName),
                        Placeholder.unparsed("region", regionId)));
                notificationService.queueNotification(targetId,
                        messages.messageFor(MessageKeys.NOTIFICATION_AGENT_REMOVED,
                                Placeholder.unparsed("player", player.getName()),
                                Placeholder.unparsed("region", regionId)));
            } else {
                sender.sendMessage(messages.messageFor(MessageKeys.AGENT_REMOVE_NOT_FOUND,
                        Placeholder.unparsed("player", targetName),
                        Placeholder.unparsed("region", regionId)));
            }
        }).exceptionally(ex -> {
            sender.sendMessage(messages.messageFor(MessageKeys.AGENT_REMOVE_ERROR,
                    Placeholder.unparsed("error", ex.getMessage())));
            return null;
        });
    }

    private static @NotNull String resolveName(@NotNull UUID uuid) {
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name != null ? name : uuid.toString();
    }
}
