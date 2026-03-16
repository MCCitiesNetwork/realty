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
import io.github.md5sha256.realty.database.Database;
import io.github.md5sha256.realty.database.SqlSessionWrapper;
import io.github.md5sha256.realty.database.mapper.LeaseContractMapper;
import io.github.md5sha256.realty.database.mapper.SaleContractMapper;
import io.github.md5sha256.realty.util.ExecutorState;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import org.apache.ibatis.exceptions.PersistenceException;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Handles {@code /realty add <player|group> [region]}.
 *
 * <p>Base permission: {@code realty.command.add}.
 * Acting on another player's region additionally requires {@code realty.command.add.others}.</p>
 */
public record AddCommand(@NotNull ExecutorState executorState,
                         @NotNull Database database) implements RealtyCommandBean, CustomCommandBean.Single<CommandSourceStack> {

    @Override
    public @NotNull LiteralArgumentBuilder<? extends CommandSourceStack> command() {
        return Commands.literal("add")
                .requires(source -> source.getSender() instanceof Player player && player.hasPermission(
                        "realty.command.add"))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(ArgumentTypes.player()::listSuggestions)
                        .executes(this::execute)
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
            try (SqlSessionWrapper wrapper = database.openSession()) {
                if (sender.hasPermission("realty.command.add.others")) {
                    return true;
                }
                SaleContractMapper saleMapper = wrapper.saleContractMapper();
                if (saleMapper.existsByRegionAndAuthority(regionId, worldId, playerId)) {
                    return true;
                }
                LeaseContractMapper leaseMapper = wrapper.leaseContractMapper();
                return leaseMapper.existsByRegionAndTenant(regionId, worldId, playerId);
            } catch (PersistenceException ex) {
                ex.printStackTrace();
                sender.sendMessage("Failed to check permissions: " + ex.getMessage());
                return false;
            }
        }, executorState.dbExec()).thenAcceptAsync(success -> {
            if (!success) {
                sender.sendMessage("You do not have permission to add players to this region!");
                return;
            }
            ProtectedRegion protectedRegion = region.region();
            if (playerOrGroup.startsWith("g:")) {
                protectedRegion.getMembers().addGroup(playerOrGroup.substring(2));
            } else {
                protectedRegion.getMembers().addPlayer(playerOrGroup);
            }
            sender.sendMessage("Successfully added " + playerOrGroup + " to region " + regionId + "!");
        }, executorState.mainThreadExec());

        return Command.SINGLE_SUCCESS;
    }

}
