package io.github.md5sha256.realty.command;

import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import io.github.md5sha256.realty.command.util.WorldGuardRegion;
import io.github.md5sha256.realty.command.util.WorldGuardRegionParser;
import io.github.md5sha256.realty.database.RealtyLogicImpl;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.util.ExecutorState;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.suggestion.Suggestion;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Handles {@code /realty remove <player|group> [region]}.
 *
 * <p>Base permission: {@code realty.command.remove}.
 * Acting on another player's region additionally requires {@code realty.command.remove.others}.</p>
 */
public record RemoveCommand(@NotNull ExecutorState executorState,
                             @NotNull RealtyLogicImpl logic,
                             @NotNull MessageContainer messages) implements CustomCommandBean.Single {

    @Override
    public @NotNull Command<CommandSourceStack> command(@NotNull CommandManager<CommandSourceStack> manager) {
        return manager.commandBuilder("realty")
                .literal("remove")
                .permission("realty.command.remove")
                .required("player", StringParser.stringParser(), playerSuggestions())
                .required("region", WorldGuardRegionParser.worldGuardRegion())
                .handler(this::execute)
                .build();
    }

    private static @NotNull SuggestionProvider<CommandSourceStack> playerSuggestions() {
        return (ctx, input) -> CompletableFuture.completedFuture(
                Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .map(Suggestion::suggestion)
                        .toList()
        );
    }

    private void execute(@NotNull CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.sender().getSender();
        if (!(sender instanceof Player player)) {
            return;
        }
        String playerOrGroup = ctx.get("player");
        WorldGuardRegion region = ctx.get("region");
        UUID playerId = player.getUniqueId();
        String regionId = region.region().getId();
        UUID worldId = region.world().getUID();

        CompletableFuture.supplyAsync(() -> {
            try {
                if (sender.hasPermission("realty.command.remove.others")) {
                    return true;
                }
                return logic.checkRegionAuthority(regionId, worldId, playerId);
            } catch (Exception ex) {
                ex.printStackTrace();
                sender.sendMessage(messages.messageFor("remove.check-permissions-error",
                        Placeholder.unparsed("error", ex.getMessage())));
                return false;
            }
        }, executorState.dbExec()).thenAcceptAsync(success -> {
            if (!success) {
                sender.sendMessage(messages.messageFor("remove.no-permission"));
                return;
            }
            ProtectedRegion protectedRegion = region.region();
            if (playerOrGroup.startsWith("g:")) {
                protectedRegion.getMembers().removeGroup(playerOrGroup.substring(2));
            } else {
                protectedRegion.getMembers().removePlayer(playerOrGroup);
            }
            sender.sendMessage(messages.messageFor("remove.success",
                    Placeholder.unparsed("target", playerOrGroup),
                    Placeholder.unparsed("region", regionId)));
        }, executorState.mainThreadExec());
    }

}
