package io.github.md5sha256.realty;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import io.github.md5sha256.realty.api.NotificationService;
import io.github.md5sha256.realty.api.ProfileApplicator;
import io.github.md5sha256.realty.api.RegionProfileService;
import io.github.md5sha256.realty.api.RegionState;
import io.github.md5sha256.realty.command.AddCommand;
import io.github.md5sha256.realty.command.AgentAddCommand;
import io.github.md5sha256.realty.command.AgentRemoveCommand;
import io.github.md5sha256.realty.command.AuctionCommandGroup;
import io.github.md5sha256.realty.command.BuyCommand;
import io.github.md5sha256.realty.command.CreateCommand;
import io.github.md5sha256.realty.command.CustomCommandBean;
import io.github.md5sha256.realty.command.DeleteCommand;
import io.github.md5sha256.realty.command.HelpCommand;
import io.github.md5sha256.realty.command.InfoCommand;
import io.github.md5sha256.realty.command.ListCommand;
import io.github.md5sha256.realty.command.OfferCommandGroup;
import io.github.md5sha256.realty.command.ReloadCommand;
import io.github.md5sha256.realty.command.RemoveCommand;
import io.github.md5sha256.realty.command.RenewCommand;
import io.github.md5sha256.realty.command.RentCommand;
import io.github.md5sha256.realty.command.SetCommandGroup;
import io.github.md5sha256.realty.command.UnsetCommandGroup;
import io.github.md5sha256.realty.command.VersionCommand;
import io.github.md5sha256.realty.command.util.WorldGuardRegion;
import io.github.md5sha256.realty.database.Database;
import io.github.md5sha256.realty.database.RealtyLogicImpl;
import io.github.md5sha256.realty.database.maria.MariaDatabase;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.settings.GroupedRegionProfile;
import io.github.md5sha256.realty.settings.RegionProfile;
import io.github.md5sha256.realty.settings.RegionProfileSettings;
import io.github.md5sha256.realty.settings.Settings;
import io.github.md5sha256.realty.util.ComponentSerializer;
import io.github.md5sha256.realty.util.EssentialsNotificationService;
import io.github.md5sha256.realty.util.ExecutorState;
import io.github.md5sha256.realty.util.SimpleDateFormatSerializer;
import io.github.md5sha256.realty.util.TransientNotificationService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.util.Tick;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.incendo.cloud.Command;
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
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class Realty extends JavaPlugin {

    private final MessageContainer messageContainer = new MessageContainer();
    private final AtomicReference<Settings> settings = new AtomicReference<>();
    private final AtomicReference<RegionProfileSettings> regionFlagSettings = new AtomicReference<>();
    private final RegionProfileService regionProfileService = new RegionProfileService(getLogger());
    private ExecutorState executorState;
    private RealtyLogicImpl logic;
    private ProfileApplicator profileApplicator;
    private DatabaseSettings databaseSettings;
    private NotificationService notificationService;
    private Database database;

    @NotNull
    public Database database() {
        return Objects.requireNonNull(this.database, "Database not initialized!");
    }

    public RealtyLogicImpl logic() {
        return this.logic;
    }

    public Settings settings() {
        return this.settings.get();
    }

    public RegionProfileSettings regionFlagSettings() {
        return this.regionFlagSettings.get();
    }

    private boolean failedLoad = false;

    @Override
    public void onLoad() {
        try {
            initDataFolder();
            copyResourceTemplate("messages.yml", "defaults/default-messages.yml");
            copyResourceTemplate("settings.yml", "defaults/default-settings.yml");
            copyResourceTemplate("profiles.yml", "defaults/default-profiles.yml");
            reloadMessages();
            this.databaseSettings = loadDatabaseSettings();
            this.settings.set(loadSettings());
            this.regionFlagSettings.set(loadRegionFlagSettings());
            configureRegionFlagService(this.regionFlagSettings.get());

            if (this.databaseSettings.url().isEmpty()) {
                getLogger().severe("Database url is empty!");
                getServer().getPluginManager().disablePlugin(this);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            failedLoad = true;
        }
    }

    @Override
    public void onEnable() {
        if (failedLoad) {
            getLogger().severe("Failed to initialize plugin, check earlier logs");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        // Plugin startup logic
        this.executorState = new ExecutorState(getServer().getScheduler()
                .getMainThreadExecutor(this), Executors.newVirtualThreadPerTaskExecutor());
        MariaDatabase mariaDatabase = new MariaDatabase(this.databaseSettings, getLogger());
        this.database = mariaDatabase;
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
        if (getServer().getPluginManager().isPluginEnabled("Essentials")) {
            getLogger().info("Detected Essentials, using essentials as the mail service");
            this.notificationService = new EssentialsNotificationService(this.executorState.mainThreadExec());
        } else {
            getLogger().info("Using the transient notification service");
            this.notificationService = new TransientNotificationService(this.executorState.mainThreadExec());
        }
        this.profileApplicator = new ProfileApplicator(
                this, this.regionProfileService, this.executorState, this.logic);
        this.profileApplicator.applyAll(this.settings.get().profileReapplyPerTick());
        scheduleTasks();
        registerCommands(this.executorState,
                this.logic,
                this.messageContainer,
                economyProvider.getProvider(),
                this.notificationService);
        getLogger().info("Plugin enabled successfully");
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        if (this.profileApplicator != null) {
            this.profileApplicator.cancel();
        }
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

    private void scheduleTasks() {
        BukkitScheduler scheduler = getServer().getScheduler();
        scheduler.runTaskLaterAsynchronously(this, () -> {
            if (this.logic == null) {
                return;
            }
            for (RealtyLogicImpl.ExpiredBidPayment payment : this.logic.clearExpiredBidPayments()) {
                this.notificationService.queueNotification(payment.bidderId(),
                        this.messageContainer.messageFor("notification.bid-payment-expired",
                                Placeholder.unparsed("region", payment.regionId()),
                                Placeholder.unparsed("amount",
                                        String.valueOf(payment.refundAmount()))));
            }
            for (RealtyLogicImpl.ExpiredOfferPayment payment : this.logic.clearExpiredOfferPayments()) {
                this.notificationService.queueNotification(payment.offererId(),
                        this.messageContainer.messageFor("notification.offer-payment-expired",
                                Placeholder.unparsed("region", payment.regionId()),
                                Placeholder.unparsed("amount",
                                        String.valueOf(payment.refundAmount()))));
            }
            List<RealtyLogicImpl.ExpiredLease> expiredLeases = this.logic.clearExpiredLeases();
            if (!expiredLeases.isEmpty()) {
                Map<String, Map<String, String>> leaseePlaceholders = new HashMap<>();
                for (RealtyLogicImpl.ExpiredLease lease : expiredLeases) {
                    leaseePlaceholders.put(lease.worldGuardRegionId(),
                            this.logic.getRegionPlaceholders(lease.worldGuardRegionId(), lease.worldId()));
                }
                scheduler.runTask(this, () -> {
                    for (RealtyLogicImpl.ExpiredLease lease : expiredLeases) {
                        World world = getServer().getWorld(lease.worldId());
                        if (world != null) {
                            RegionManager regionManager = WorldGuard.getInstance()
                                    .getPlatform()
                                    .getRegionContainer()
                                    .get(BukkitAdapter.adapt(world));
                            if (regionManager != null) {
                                ProtectedRegion protectedRegion = regionManager.getRegion(lease.worldGuardRegionId());
                                if (protectedRegion != null) {
                                    protectedRegion.getMembers().removePlayer(lease.tenantId());
                                    regionProfileService.applyFlags(
                                            new WorldGuardRegion(protectedRegion, world),
                                            RegionState.FOR_LEASE,
                                            leaseePlaceholders.getOrDefault(lease.worldGuardRegionId(), Map.of()));
                                }
                            }
                        }
                        this.notificationService.queueNotification(lease.tenantId(),
                                this.messageContainer.messageFor("notification.lease-expired",
                                        Placeholder.unparsed("region",
                                                lease.worldGuardRegionId())));
                        this.notificationService.queueNotification(lease.landlordId(),
                                this.messageContainer.messageFor(
                                        "notification.lease-expired-landlord",
                                        Placeholder.unparsed("region",
                                                lease.worldGuardRegionId())));
                    }
                });
            }
        }, Tick.tick().fromDuration(Duration.ofMinutes(1)));
    }

    private void initDataFolder() throws IOException {
        File dataFolder = getDataFolder();
        if (!dataFolder.isDirectory()) {
            Files.createDirectory(dataFolder.toPath());
        }
        File defaultsFolder = new File(dataFolder, "defaults");
        if (!defaultsFolder.isDirectory()) {
            Files.createDirectory(defaultsFolder.toPath());
        }
    }

    private Settings loadSettings() throws IOException {
        ConfigurationNode settingsRoot = copyDefaultsYaml("settings");
        return settingsRoot.get(Settings.class);
    }

    private DatabaseSettings loadDatabaseSettings() throws IOException {
        ConfigurationNode settingsRoot = copyDefaultsYaml("database");
        return settingsRoot.get(DatabaseSettings.class);
    }

    private RegionProfileSettings loadRegionFlagSettings() throws IOException {
        ConfigurationNode settingsRoot = copyDefaultsYaml("profiles");
        return settingsRoot.get(RegionProfileSettings.class);
    }

    private void reloadMessages() throws IOException {
        ConfigurationNode node = copyDefaultsYaml("messages");
        this.messageContainer.load(node);
    }

    private void configureRegionFlagService(@NotNull RegionProfileSettings settings) {
        this.regionProfileService.clearGroupedFlagProfiles();
        Map<RegionState, RegionProfile> global = settings.global();
        if (global != null) {
            for (Map.Entry<RegionState, RegionProfile> entry : global.entrySet()) {
                this.regionProfileService.setGlobalFlagProfile(
                        entry.getKey(), entry.getValue().priority(), entry.getValue().flags());
            }
        }
        List<GroupedRegionProfile> grouped = settings.grouped();
        if (grouped != null) {
            for (GroupedRegionProfile group : grouped) {
                Map<RegionState, RegionProfileService.FlagProfile> stateProfiles = new HashMap<>();
                for (Map.Entry<RegionState, RegionProfile> entry : group.states().entrySet()) {
                    stateProfiles.put(entry.getKey(),
                            new RegionProfileService.FlagProfile(
                                    entry.getValue().priority(), entry.getValue().flags()));
                }
                this.regionProfileService.addGroupedFlagProfile(group.regions(), stateProfiles);
            }
        }
    }

    private void performReload() throws IOException {
        this.settings.set(loadSettings());
        this.regionFlagSettings.set(loadRegionFlagSettings());
        configureRegionFlagService(this.regionFlagSettings.get());
        this.profileApplicator.applyAll(this.settings.get().profileReapplyPerTick());
        reloadMessages();
    }

    private void registerCommands(
            @NotNull ExecutorState executorState,
            @NotNull RealtyLogicImpl logic,
            @NotNull MessageContainer messageContainer,
            @NotNull Economy economy,
            @NotNull NotificationService notificationService
    ) {
        String version = getPluginMeta().getVersion();
        List<CustomCommandBean> commands = List.of(
                new VersionCommand(version),
                new AddCommand(executorState, logic, messageContainer),
                new AgentAddCommand(executorState, logic, messageContainer),
                new AgentRemoveCommand(executorState, logic, messageContainer),
                new AuctionCommandGroup(executorState,
                        logic,
                        economy,
                        notificationService,
                        this.regionProfileService,
                        this.settings.get(),
                        messageContainer),
                new BuyCommand(executorState,
                        logic,
                        economy,
                        notificationService,
                        this.regionProfileService,
                        messageContainer),
                new CreateCommand(executorState, logic, this.settings, this.regionProfileService, messageContainer),
                new DeleteCommand(executorState, logic, this.regionProfileService, messageContainer),
                new HelpCommand(messageContainer),
                new InfoCommand(executorState, logic, this.settings.get(), messageContainer),
                new ListCommand(executorState, logic, messageContainer),
                new OfferCommandGroup(executorState,
                        logic,
                        economy,
                        notificationService,
                        this.regionProfileService,
                        messageContainer),
                new RenewCommand(executorState, logic, economy, messageContainer),
                new RentCommand(executorState,
                        logic,
                        economy,
                        notificationService,
                        this.regionProfileService,
                        messageContainer),
                new SetCommandGroup(executorState, logic, this.regionProfileService, messageContainer),
                new UnsetCommandGroup(this.regionProfileService, executorState, logic, messageContainer),
                new ReloadCommand(executorState, () -> {
                    performReload();
                    return null;
                }, messageContainer),
                new RemoveCommand(executorState, logic, messageContainer)
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

    private void copyResourceTemplate(@NotNull String resourceName,
                                      @NotNull String targetName) throws IOException {
        File file = new File(getDataFolder(), targetName);
        try (InputStream inputStream = getResource(resourceName)) {
            if (inputStream == null) {
                getLogger().severe("Failed to find resource: " + resourceName);
                return;
            }
            try (FileOutputStream fileOutputStream = new FileOutputStream(file)) {
                inputStream.transferTo(fileOutputStream);
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
                .defaultOptions(options -> options.serializers(builder -> builder
                        .register(Component.class, ComponentSerializer.MINI_MESSAGE)
                        .register(SimpleDateFormat.class, SimpleDateFormatSerializer.INSTANCE)))
                .nodeStyle(NodeStyle.BLOCK);
    }
}
