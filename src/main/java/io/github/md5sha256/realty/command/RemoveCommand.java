package io.github.md5sha256.realty.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import io.github.md5sha256.realty.command.util.WorldGuardRegion;
import io.github.md5sha256.realty.command.util.WorldGuardRegionArgument;
import io.github.md5sha256.realty.command.util.WorldGuardRegionResolver;
import io.github.md5sha256.realty.database.RealtyLogicImpl;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.util.ExecutorState;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.apache.ibatis.exceptions.PersistenceException;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
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
                             @NotNull MessageContainer messages) implements RealtyCommandBean, CustomCommandBean.Single<CommandSourceStack> {

    @Override
    public @NotNull LiteralArgumentBuilder<? extends CommandSourceStack> command() {
        return Commands.literal("remove")
                .requires(source -> source.getSender() instanceof Player player && player.hasPermission(
                        "realty.command.remove"))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(ArgumentTypes.player()::listSuggestions)
                        .then(Commands.argument("region", new WorldGuardRegionArgument())
                                .executes(this::execute)));
    }

    private int execute(@NotNull CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Player player = (Player) ctx.getSource().getSender();
        String playerOrGroup = ctx.getArgument("player", String.class);
        WorldGuardRegion region = WorldGuardRegionResolver.resolve(ctx, "region").resolve();
        CommandSender sender = ctx.getSource().getSender();
        UUID playerId = player.getUniqueId();
        String regionId = region.region().getId();
        UUID worldId = region.world().getUID();

        CompletableFuture.supplyAsync(() -> {
            try {
                if (sender.hasPermission("realty.command.remove.others")) {
                    return true;
                }
                return logic.checkRegionAuthority(regionId, worldId, playerId);
            } catch (PersistenceException ex) {
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

        return Command.SINGLE_SUCCESS;
    }

}
