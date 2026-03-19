package io.github.md5sha256.realty.command;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.managers.storage.StorageException;
import io.github.md5sha256.realty.command.util.WorldGuardRegion;
import io.github.md5sha256.realty.command.util.WorldGuardRegionParser;
import io.github.md5sha256.realty.database.RealtyLogicImpl;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.util.ExecutorState;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import org.bukkit.command.CommandSender;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.BooleanParser;
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
                            @NotNull RealtyLogicImpl logic,
                            @NotNull MessageContainer messages) implements CustomCommandBean.Single {

    @Override
    public @NotNull Command<CommandSourceStack> command(@NotNull CommandManager<CommandSourceStack> manager) {
        return manager.commandBuilder("realty")
                .literal("delete")
                .permission("realty.command.delete")
                .required("region", WorldGuardRegionParser.worldGuardRegion())
                .optional("includeworldguard", BooleanParser.booleanParser())
                .handler(this::execute)
                .build();
    }

    private void execute(@NotNull CommandContext<CommandSourceStack> ctx) {
        WorldGuardRegion region = ctx.get("region");
        boolean includeWorldGuard = ctx.getOrDefault("includeworldguard", false);

        CommandSender sender = ctx.sender().getSender();

        if (includeWorldGuard && !sender.hasPermission("realty.command.delete.includeworldguard")) {
            sender.sendMessage(messages.messageFor("common.no-permission"));
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                int deleted = logic.deleteRegion(region.region().getId(), region.world().getUID());
                if (deleted == 0) {
                    sender.sendMessage(messages.messageFor("delete.not-registered"));
                    return;
                }

                if (includeWorldGuard) {
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
                            sender.sendMessage(messages.messageFor("delete.worldguard-save-error",
                                    Placeholder.unparsed("error", ex.getMessage())));
                            return;
                        }
                    }
                }

                sender.sendMessage(messages.messageFor("delete.success"));
            } catch (Exception ex) {
                ex.printStackTrace();
                sender.sendMessage(messages.messageFor("delete.error",
                        Placeholder.unparsed("error", ex.getMessage())));
            }
        }, executorState.dbExec());
    }

}
