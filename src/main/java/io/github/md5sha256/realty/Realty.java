package io.github.md5sha256.realty;

import io.github.md5sha256.realty.command.AcceptOfferCommand;
import io.github.md5sha256.realty.command.AddCommand;
import io.github.md5sha256.realty.command.AuctionCommand;
import io.github.md5sha256.realty.command.BidCommand;
import io.github.md5sha256.realty.command.CancelAuctionCommand;
import io.github.md5sha256.realty.command.CreateCommand;
import io.github.md5sha256.realty.command.CustomCommandBean;
import io.github.md5sha256.realty.command.DeleteCommand;
import io.github.md5sha256.realty.command.InfoCommand;
import io.github.md5sha256.realty.command.ListCommand;
import io.github.md5sha256.realty.command.OfferCommand;
import io.github.md5sha256.realty.command.OffersCommand;
import io.github.md5sha256.realty.command.PayBidCommand;
import io.github.md5sha256.realty.command.PayOfferCommand;
import io.github.md5sha256.realty.command.ReloadCommand;
import io.github.md5sha256.realty.command.RemoveCommand;
import io.github.md5sha256.realty.command.WithdrawOfferCommand;
import io.github.md5sha256.realty.database.RealtyLogicImpl;
import io.github.md5sha256.realty.database.maria.MariaDatabase;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.settings.Settings;
import io.github.md5sha256.realty.util.ComponentSerializer;
import io.github.md5sha256.realty.util.ExecutorState;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.java.JavaPlugin;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.brigadier.BrigadierSetting;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.paper.PaperCommandManager;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class Realty extends JavaPlugin {

    private final MessageContainer messageContainer = new MessageContainer();
    private final AtomicReference<Settings> settings = new AtomicReference<>();
    private ExecutorState executorState;
    private RealtyLogicImpl logic;
    private DatabaseSettings databaseSettings;

    @Override
    public void onLoad() {
        try {
            initDataFolder();
            reloadMessages();
            this.databaseSettings = loadDatabaseSettings();
            this.settings.set(loadSettings());
            if (this.databaseSettings.url().isEmpty()) {
                getLogger().severe("Database url is empty!");
                getServer().getPluginManager().disablePlugin(this);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onEnable() {
        // Plugin startup logic
        this.executorState = new ExecutorState(getServer().getScheduler()
                .getMainThreadExecutor(this), Executors.newVirtualThreadPerTaskExecutor());
        MariaDatabase mariaDatabase = new MariaDatabase(this.databaseSettings, getLogger());
        try {
            mariaDatabase.initializeSchema(Path.of("sql/migrations"));
        } catch (IOException | SQLException ex) {
            getLogger().severe("Schema migration failed!");
            ex.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        this.logic = new RealtyLogicImpl(mariaDatabase);
        var economyProvider = getServer().getServicesManager().getRegistration(Economy.class);
        if (economyProvider == null) {
            getLogger().severe("Economy not found, plugin will now disable!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        registerCommands(this.executorState,
                this.logic,
                this.messageContainer,
                economyProvider.getProvider());
        getLogger().info("Plugin enabled successfully");
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        if (this.executorState != null) {
            try (ExecutorService service = this.executorState.dbExec();) {
                service.shutdownNow();
                if (!service.awaitTermination(30, TimeUnit.SECONDS)) {
                    getLogger().severe("Failed to await database threadpool shutdown!");
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                ex.printStackTrace();
            }
        }
        getLogger().info("Plugin disabled successfully");
    }

    private void initDataFolder() throws IOException {
        File dataFolder = getDataFolder();
        if (!dataFolder.isDirectory()) {
            Files.createDirectory(dataFolder.toPath());
        }
    }

    private Settings loadSettings() throws IOException {
        ConfigurationNode settingsRoot = copyDefaultsYaml("settings");
        return settingsRoot.get(Settings.class);
    }

    private DatabaseSettings loadDatabaseSettings() throws IOException {
        ConfigurationNode settingsRoot = copyDefaultsYaml("database-settings");
        return settingsRoot.get(DatabaseSettings.class);
    }

    private void reloadMessages() throws IOException {
        ConfigurationNode node = copyDefaultsYaml("messages");
        this.messageContainer.load(node);
    }

    private void performReload() throws IOException {
        this.settings.set(loadSettings());
        reloadMessages();
    }

    private void registerCommands(
            @NotNull ExecutorState executorState,
            @NotNull RealtyLogicImpl logic,
            @NotNull MessageContainer messageContainer,
            @NotNull Economy economy
    ) {
        List<CustomCommandBean> commands = List.of(
                new AcceptOfferCommand(executorState, logic, messageContainer),
                new AddCommand(executorState, logic, messageContainer),
                new AuctionCommand(executorState, logic, messageContainer),
                new BidCommand(executorState, logic, messageContainer),
                new CancelAuctionCommand(executorState, logic, messageContainer),
                new CreateCommand(executorState, logic, this.settings, messageContainer),
                new DeleteCommand(executorState, logic, messageContainer),
                new InfoCommand(executorState, logic, messageContainer),
                new ListCommand(executorState, logic, messageContainer),
                new OffersCommand(executorState, logic, messageContainer),
                new OfferCommand(executorState, logic, messageContainer),
                new PayBidCommand(executorState, logic, economy, messageContainer),
                new PayOfferCommand(executorState, logic, economy, messageContainer),
                new ReloadCommand(executorState, () -> {
                    performReload();
                    return null;
                }, messageContainer),
                new RemoveCommand(executorState, logic, messageContainer),
                new WithdrawOfferCommand(executorState, logic, messageContainer)
        );

        var manager = PaperCommandManager.builder()
                .executionCoordinator(ExecutionCoordinator.simpleCoordinator())
                .buildOnEnable(this);
        manager.brigadierManager().setNativeNumberSuggestions(true);
        for (CustomCommandBean bean : commands) {
            for (Command<CommandSourceStack> cmd : bean.commands(manager)) {
                manager.command(cmd);
            }
        }
    }

    private ConfigurationNode copyDefaultsYaml(@NotNull String resourceName) throws IOException {
        String fileName = resourceName + ".yml";
        File file = new File(getDataFolder(), fileName);
        if (!file.exists()) {
            try (FileOutputStream fileOutputStream = new FileOutputStream(file);
                 InputStream inputStream = getResource(fileName)) {
                if (inputStream == null) {
                    getLogger().severe("Failed to copy default messages!");
                } else {
                    inputStream.transferTo(fileOutputStream);
                }
            }
        }
        YamlConfigurationLoader existingLoader = yamlLoader()
                .file(file)
                .build();
        return existingLoader.load();
    }


    private YamlConfigurationLoader.Builder yamlLoader() {
        return YamlConfigurationLoader.builder()
                .defaultOptions(options -> options.serializers(builder -> builder.register(Component.class,
                        ComponentSerializer.MINI_MESSAGE)))
                .nodeStyle(NodeStyle.BLOCK);
    }
}
