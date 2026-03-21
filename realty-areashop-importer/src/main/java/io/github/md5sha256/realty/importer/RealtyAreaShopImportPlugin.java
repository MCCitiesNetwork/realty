package io.github.md5sha256.realty.importer;

import com.mojang.brigadier.Command;
import io.github.md5sha256.realty.Realty;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class RealtyAreaShopImportPlugin extends JavaPlugin {

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Override
    public void onEnable() {
        registerCommands();
        getLogger().info("Plugin enabled!");
    }

    @Override
    public void onDisable() {
        this.executorService.shutdownNow();
        try {
            this.executorService.awaitTermination(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            getLogger().warning("Failed to shutdown executor service after 30 seconds");
        }
        getLogger().info("Plugin disabled!");
    }

    private void registerCommands() {
        Realty realty = (Realty) getServer().getPluginManager().getPlugin("Realty");
        if (realty == null) {
            throw new IllegalStateException("Realty plugin not found!");
        }
        var command = Commands.literal("realtyimport")
                .requires(source -> source.getSender().hasPermission("realty.import"))
                .executes(ctx -> {
                    var sender = ctx.getSource().getSender();
                    ImportJob.performImport(realty.database(), realty.settings(), this.executorService, sender)
                            .thenAccept(result -> {
                                sender.sendMessage(Component.text(
                                        "Import complete: " + result.imported() + " imported, "
                                                + result.skipped() + " skipped, "
                                                + result.failed() + " failed",
                                        NamedTextColor.GREEN));
                            }).exceptionally(ex -> {
                                sender.sendMessage(Component.text(
                                        "Import failed: " + ex.getMessage(), NamedTextColor.RED));
                                getLogger().severe("Import failed with exception");
                                ex.printStackTrace();
                                return null;
                            });
                    return Command.SINGLE_SUCCESS;
                })
                .build();
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar()
                    .register(command,
                            "Import AreaShop regions into Realty",
                            List.of("areashopimport"));
        });
    }
}
