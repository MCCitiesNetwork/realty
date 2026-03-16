package io.github.md5sha256.realty.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.managers.storage.StorageException;
import io.github.md5sha256.realty.command.util.WorldGuardRegion;
import io.github.md5sha256.realty.command.util.WorldGuardRegionArgument;
import io.github.md5sha256.realty.command.util.WorldGuardRegionResolver;
import io.github.md5sha256.realty.database.RealtyLogicImpl;
import io.github.md5sha256.realty.util.ExecutorState;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.apache.ibatis.exceptions.PersistenceException;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * Handles {@code /realty delete <region> [includeworldguard]}.
 *
 * <p>Base permission: {@code realty.command.delete}.
 * Passing the {@code includeworldguard} flag additionally requires
 * {@code realty.command.delete.includeworldguard}.</p>
 */
public record DeleteCommand(@NotNull ExecutorState executorState,
                            @NotNull RealtyLogicImpl logic) implements RealtyCommandBean, CustomCommandBean.Single<CommandSourceStack> {

    @Override
    public @NotNull LiteralArgumentBuilder<? extends CommandSourceStack> command() {
        return Commands.literal("delete")
                .requires(source -> source.getSender().hasPermission("realty.command.delete"))
                .then(Commands.argument("region", new WorldGuardRegionArgument())
                        .executes(this::execute)
                        .then(Commands.argument("includeworldguard", BoolArgumentType.bool())
                                .requires(source -> source.getSender().hasPermission("realty.command.delete.includeworldguard"))
                                .executes(this::execute)));
    }

    private int execute(@NotNull CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        WorldGuardRegion region = WorldGuardRegionResolver.resolve(ctx, "region").resolve();

        boolean includeWorldGuard;
        try {
            includeWorldGuard = BoolArgumentType.getBool(ctx, "includeworldguard");
        } catch (IllegalArgumentException ignored) {
            includeWorldGuard = false;
        }
        boolean finalIncludeWorldGuard = includeWorldGuard;

        CommandSender sender = ctx.getSource().getSender();
        CompletableFuture.runAsync(() -> {
            try {
                int deleted = logic.deleteRegion(region.region().getId(), region.world().getUID());
                if (deleted == 0) {
                    sender.sendMessage("Region is not registered in Realty!");
                    return;
                }

                if (finalIncludeWorldGuard) {
                    RegionManager regionManager = WorldGuard.getInstance()
                            .getPlatform()
                            .getRegionContainer()
                            .get(BukkitAdapter.adapt(region.world()));
                    if (regionManager != null) {
                        regionManager.removeRegion(region.region().getId());
                        try {
                            regionManager.save();
                        } catch (StorageException ex) {
                            ex.printStackTrace();
                            sender.sendMessage("Failed to save WorldGuard regions: " + ex.getMessage());
                            return;
                        }
                    }
                }

                sender.sendMessage("Region deleted successfully!");
            } catch (PersistenceException ex) {
                ex.printStackTrace();
                sender.sendMessage("Failed to delete region: " + ex.getMessage());
            }
        }, executorState.dbExec());
        return Command.SINGLE_SUCCESS;
    }

}
