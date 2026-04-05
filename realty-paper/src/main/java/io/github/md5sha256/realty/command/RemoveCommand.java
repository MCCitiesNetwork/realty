package io.github.md5sha256.realty.command;

import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import io.github.md5sha256.realty.api.WorldGuardRegion;
import io.github.md5sha256.realty.command.util.WorldGuardRegionResolver;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.localisation.MessageKeys;
import org.incendo.cloud.paper.util.sender.Source;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.suggestion.Suggestion;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * Handles {@code /realty remove <player|group> [region]}.
 *
 * <p>Base permission: {@code realty.command.remove}.
 * Acting on another player's region additionally requires {@code realty.command.remove.others}.</p>
 */
public record RemoveCommand(@NotNull MessageContainer messages) implements CustomCommandBean.Single {

    @Override
    public @NotNull Command<? extends Source> command(@NotNull Command.Builder<Source> builder) {
        return builder
                .literal("remove")
                .permission("realty.command.remove")
                .required("player", StringParser.stringParser(), playerSuggestions())
                .optional("region", WorldGuardRegionResolver.worldGuardRegionResolver())
                .handler(this::execute)
                .build();
    }

    private static @NotNull SuggestionProvider<Source> playerSuggestions() {
        return (ctx, input) -> CompletableFuture.completedFuture(
                Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .map(Suggestion::suggestion)
                        .toList()
        );
    }

    private void execute(@NotNull CommandContext<Source> ctx) {
        CommandSender sender = ctx.sender().source();
        String playerOrGroup = ctx.get("player");
        WorldGuardRegion region = ctx.<WorldGuardRegion>optional("region")
                .orElseGet(() -> sender instanceof Player player
                        ? WorldGuardRegionResolver.resolveAtLocation(player.getLocation()) : null);
        if (region == null) {
            sender.sendMessage(messages.messageFor(MessageKeys.ERROR_NO_REGION));
            return;
        }
        String regionId = region.region().getId();

        if (sender instanceof Player player
                && !sender.hasPermission("realty.command.remove.others")
                && !region.region().getOwners().contains(player.getUniqueId())) {
            sender.sendMessage(messages.messageFor(MessageKeys.REMOVE_NO_PERMISSION));
            return;
        }
        ProtectedRegion protectedRegion = region.region();
        if (playerOrGroup.startsWith("g:")) {
            protectedRegion.getMembers().removeGroup(playerOrGroup.substring(2));
        } else {
            OfflinePlayer target = Bukkit.getOfflinePlayer(playerOrGroup);
            protectedRegion.getMembers().removePlayer(target.getUniqueId());
        }
        sender.sendMessage(messages.messageFor(MessageKeys.REMOVE_SUCCESS,
                Placeholder.unparsed("target", playerOrGroup),
                Placeholder.unparsed("region", regionId)));
    }

}
