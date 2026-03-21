package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.command.util.AuthorityParser;
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
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Handles {@code /realty agent add <player> <region>}.
 *
 * <p>Adds a player to the sanctioned auctioneers list for a region.</p>
 *
 * <p>Permission: {@code realty.command.agent.add}.</p>
 */
public record AgentAddCommand(@NotNull ExecutorState executorState,
                               @NotNull RealtyLogicImpl logic,
                               @NotNull MessageContainer messages) implements CustomCommandBean.Single {

    @Override
    public @NotNull Command<CommandSourceStack> command(@NotNull CommandManager<CommandSourceStack> manager) {
        return manager.commandBuilder("realty")
                .literal("agent")
                .literal("add")
                .permission("realty.command.agent.add")
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
        UUID targetId = ctx.get("player");
        WorldGuardRegion region = ctx.get("region");
        String regionId = region.region().getId();
        UUID worldId = region.world().getUID();
        String targetName = resolveName(targetId);
        CompletableFuture.runAsync(() -> {
            try {
                int rows = logic.addSanctionedAuctioneer(regionId, worldId, targetId);
                if (rows > 0) {
                    sender.sendMessage(messages.messageFor("agent-add.success",
                            Placeholder.unparsed("player", targetName),
                            Placeholder.unparsed("region", regionId)));
                } else {
                    sender.sendMessage(messages.messageFor("agent-add.failed",
                            Placeholder.unparsed("player", targetName),
                            Placeholder.unparsed("region", regionId)));
                }
            } catch (Exception ex) {
                sender.sendMessage(messages.messageFor("agent-add.error",
                        Placeholder.unparsed("error", ex.getMessage())));
            }
        }, executorState.dbExec());
    }

    private static @NotNull String resolveName(@NotNull UUID uuid) {
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name != null ? name : uuid.toString();
    }
}
