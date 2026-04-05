package io.github.md5sha256.realty;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import io.github.md5sha256.realty.api.CurrencyFormatter;
import io.github.md5sha256.realty.api.NotificationService;
import io.github.md5sha256.realty.api.ProfileApplicator;
import io.github.md5sha256.realty.api.RegionProfileService;
import io.github.md5sha256.realty.api.RegionState;
import io.github.md5sha256.realty.api.SignCache;
import io.github.md5sha256.realty.api.SignTextApplicator;
import io.github.md5sha256.realty.command.AddCommand;
import io.github.md5sha256.realty.command.AgentInviteAcceptCommand;
import io.github.md5sha256.realty.command.AgentInviteCommand;
import io.github.md5sha256.realty.command.AgentInviteRejectCommand;
import io.github.md5sha256.realty.command.AgentInviteWithdrawCommand;
import io.github.md5sha256.realty.command.AgentRemoveCommand;
import io.github.md5sha256.realty.command.AuctionCommandGroup;
import io.github.md5sha256.realty.command.BuyCommand;
import io.github.md5sha256.realty.command.CreateCommand;
import io.github.md5sha256.realty.command.RegisterCommand;
import io.github.md5sha256.realty.command.CustomCommandBean;
import io.github.md5sha256.realty.command.DeleteCommand;
import io.github.md5sha256.realty.command.HelpCommand;
import io.github.md5sha256.realty.command.HistoryCommand;
import io.github.md5sha256.realty.command.InfoCommand;
import io.github.md5sha256.realty.command.ListCommand;
import io.github.md5sha256.realty.command.OfferCommandGroup;
import io.github.md5sha256.realty.command.ReloadCommand;
import io.github.md5sha256.realty.command.RemoveCommand;
import io.github.md5sha256.realty.command.ExtendCommand;
import io.github.md5sha256.realty.command.RentCommand;
import io.github.md5sha256.realty.command.UnrentCommand;
import io.github.md5sha256.realty.command.SetCommandGroup;
import io.github.md5sha256.realty.command.SignCommand;
import io.github.md5sha256.realty.command.SubregionCommandGroup;
import io.github.md5sha256.realty.command.TeleportCommand;
import io.github.md5sha256.realty.command.UnsetCommandGroup;
import io.github.md5sha256.realty.command.VersionCommand;
import io.github.md5sha256.realty.command.util.SafeLocationFinder;
import io.github.md5sha256.realty.command.util.WorldGuardRegion;
import io.github.md5sha256.realty.database.Database;
import io.github.md5sha256.realty.api.RealtyBackend;
import io.github.md5sha256.realty.api.RealtyPaperApi;
import io.github.md5sha256.realty.api.RealtyPaperApiImpl;
import io.github.md5sha256.realty.database.RealtyBackendImpl;
import io.github.md5sha256.realty.database.maria.MariaDatabase;
import io.github.md5sha256.realty.listener.SignInteractionListener;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.localisation.MessageKeys;
import io.github.md5sha256.realty.settings.GroupedRegionProfile;
import io.github.md5sha256.realty.settings.RegionProfile;
import io.github.md5sha256.realty.settings.RegionProfileSettings;
import io.github.md5sha256.realty.settings.Settings;
import io.github.md5sha256.realty.util.ComponentSerializer;
import io.github.md5sha256.realty.util.DateFormatter;
import io.github.md5sha256.realty.util.EssentialsNotificationService;
import io.github.md5sha256.realty.util.EssentialsSafeBlockPredicate;
import io.github.md5sha256.realty.util.ExecutorState;
import io.github.md5sha256.realty.util.SimpleDateFormatSerializer;
import io.github.md5sha256.realty.util.TransientNotificationService;
import org.incendo.cloud.paper.util.sender.PaperSimpleSenderMapper;
import org.incendo.cloud.paper.util.sender.Source;
import io.papermc.paper.util.Tick;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.incendo.cloud.Command;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.paper.PaperCommandManager;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class Realty extends JavaPlugin {

    private final MessageContainer messageContainer = new MessageContainer();
    private final AtomicReference<Settings> settings = new AtomicReference<>();
    private final AtomicReference<RegionProfileSettings> regionFlagSettings = new AtomicReference<>();
    private final RegionProfileService regionProfileService = new RegionProfileService(getLogger());
    private ExecutorState executorState;
    private RealtyBackend logic;
    private ProfileApplicator profileApplicator;
    private DatabaseSettings databaseSettings;
    private NotificationService notificationService;
    private Database database;
    private final SignCache signCache = new SignCache();
    private SignTextApplicator signTextApplicator;
    private RealtyPaperApi paperApi;

    @NotNull
    public Database database() {
        return Objects.requireNonNull(this.database, "Database not initialized!");
    }

    public RealtyBackend logic() {
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
        ClassLoader pluginClassLoader = getClass().getClassLoader();
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable);
            thread.setContextClassLoader(pluginClassLoader);
            return thread;
        };
        this.executorState = new ExecutorState(getServer().getScheduler()
                .getMainThreadExecutor(this), Executors.newFixedThreadPool(4, threadFactory));
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
        this.logic = new RealtyBackendImpl(mariaDatabase, uuid -> {
            OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
            return player.getName() != null ? player.getName() : uuid.toString();
        }, dateTime -> DateFormatter.format(this.settings.get(), dateTime),
                () -> this.settings.get().offerPaymentDurationSeconds());
        var economyProvider = getServer().getServicesManager().getRegistration(Economy.class);
        if (economyProvider == null) {
            getLogger().severe("Economy not found, plugin will now disable!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        SafeLocationFinder safeLocationFinder;
        if (getServer().getPluginManager().isPluginEnabled("Essentials")) {
            getLogger().info("Detected Essentials, using essentials as the mail service");
            this.notificationService = new EssentialsNotificationService(this.executorState.mainThreadExec());
            getLogger().info("Using EssentialsX safe-block predicate for teleportation");
            safeLocationFinder = new SafeLocationFinder(new EssentialsSafeBlockPredicate());
        } else {
            getLogger().info("Using the transient notification service");
            this.notificationService = new TransientNotificationService(this.executorState.mainThreadExec());
            safeLocationFinder = new SafeLocationFinder();
        }
        this.signTextApplicator = new SignTextApplicator(
                this.regionProfileService, this.logic, this.database, this.signCache, getLogger());
        this.profileApplicator = new ProfileApplicator(
                this, this.regionProfileService, this.executorState, this.logic,
                this.signTextApplicator, this.signCache);
        this.profileApplicator.applyAll(this.settings.get().profileReapplyPerTick());
        getServer().getPluginManager().registerEvents(
                new SignInteractionListener(this.database, this.logic,
                        this.regionProfileService, this.executorState, this.signCache,
                        this.signTextApplicator, this.messageContainer), this);
        this.paperApi = new RealtyPaperApiImpl(
                this.logic, economyProvider.getProvider(), this.executorState, this.database,
                this.regionProfileService, this.signTextApplicator, this.signCache);
        scheduleTasks();
        registerCommands(this.paperApi,
                this.executorState,
                this.messageContainer,
                this.notificationService,
                safeLocationFinder);
        getServer().getServicesManager().register(RealtyBackend.class, this.logic, this, ServicePriority.Normal);
        getServer().getServicesManager().register(RealtyPaperApi.class, this.paperApi, this, ServicePriority.Normal);
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
        if (this.database != null) {
            try {
                this.database.close();
            } catch (IOException ex) {
                getLogger().severe("Failed to close database connection pool: " + ex.getMessage());
            }
        }
        getLogger().info("Plugin disabled successfully");
    }

    private void scheduleTasks() {
        BukkitScheduler scheduler = getServer().getScheduler();
        long intervalTicks = Tick.tick().fromDuration(Duration.ofMinutes(1));
        scheduler.runTaskTimerAsynchronously(this, () -> {
            if (this.logic == null) {
                return;
            }
            for (RealtyBackend.ExpiredBiddingAuction auction : this.logic.clearExpiredBiddingAuctions()) {
                if (auction.winnerId() != null) {
                    this.notificationService.queueNotification(auction.winnerId(),
                            this.messageContainer.messageFor(MessageKeys.NOTIFICATION_AUCTION_WON,
                                    Placeholder.unparsed("region", auction.worldGuardRegionId())));
                } else {
                    this.notificationService.queueNotification(auction.auctioneerId(),
                            this.messageContainer.messageFor(MessageKeys.NOTIFICATION_AUCTION_ENDED_NO_BIDS,
                                    Placeholder.unparsed("region", auction.worldGuardRegionId())));
                }
            }
            for (RealtyBackend.ExpiredBidPayment payment : this.logic.clearExpiredBidPayments()) {
                this.notificationService.queueNotification(payment.bidderId(),
                        this.messageContainer.messageFor(MessageKeys.NOTIFICATION_BID_PAYMENT_EXPIRED,
                                Placeholder.unparsed("region", payment.regionId()),
                                Placeholder.unparsed("amount",
                                        CurrencyFormatter.format(payment.refundAmount()))));
            }
            for (RealtyBackend.ExpiredOfferPayment payment : this.logic.clearExpiredOfferPayments()) {
                this.notificationService.queueNotification(payment.offererId(),
                        this.messageContainer.messageFor(MessageKeys.NOTIFICATION_OFFER_PAYMENT_EXPIRED,
                                Placeholder.unparsed("region", payment.regionId()),
                                Placeholder.unparsed("amount",
                                        CurrencyFormatter.format(payment.refundAmount()))));
            }
            List<RealtyBackend.ExpiredLeasehold> expiredLeaseholds = this.logic.clearExpiredLeaseholds();
            if (!expiredLeaseholds.isEmpty()) {
                Map<String, Map<String, String>> leaseholdPlaceholders = new HashMap<>();
                for (RealtyBackend.ExpiredLeasehold expired : expiredLeaseholds) {
                    leaseholdPlaceholders.put(expired.worldGuardRegionId(),
                            this.logic.getRegionPlaceholders(expired.worldGuardRegionId(), expired.worldId()));
                }
                scheduler.runTask(this, () -> {
                    for (RealtyBackend.ExpiredLeasehold expired : expiredLeaseholds) {
                        World world = getServer().getWorld(expired.worldId());
                        if (world != null) {
                            RegionManager regionManager = WorldGuard.getInstance()
                                    .getPlatform()
                                    .getRegionContainer()
                                    .get(BukkitAdapter.adapt(world));
                            if (regionManager != null) {
                                ProtectedRegion protectedRegion = regionManager.getRegion(expired.worldGuardRegionId());
                                if (protectedRegion != null) {
                                    protectedRegion.getOwners().removePlayer(expired.tenantId());
                                    regionProfileService.applyFlags(
                                            new WorldGuardRegion(protectedRegion, world),
                                            RegionState.FOR_LEASE,
                                            leaseholdPlaceholders.getOrDefault(expired.worldGuardRegionId(), Map.of()));
                                }
                            }
                        }
                        this.notificationService.queueNotification(expired.tenantId(),
                                this.messageContainer.messageFor(MessageKeys.NOTIFICATION_LEASEHOLD_EXPIRED,
                                        Placeholder.unparsed("region",
                                                expired.worldGuardRegionId())));
                        this.notificationService.queueNotification(expired.landlordId(),
                                this.messageContainer.messageFor(
                                        MessageKeys.NOTIFICATION_LEASEHOLD_EXPIRED_LANDLORD,
                                        Placeholder.unparsed("region",
                                                expired.worldGuardRegionId())));
                    }
                });
            }
        }, intervalTicks, intervalTicks);
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
        this.regionProfileService.clearGroupedSignProfiles();
        Map<RegionState, RegionProfile> global = settings.global();
        if (global != null) {
            for (Map.Entry<RegionState, RegionProfile> entry : global.entrySet()) {
                this.regionProfileService.setGlobalFlagProfile(
                        entry.getKey(), entry.getValue().priority(), entry.getValue().flags());
                if (entry.getValue().sign() != null) {
                    this.regionProfileService.setGlobalSignProfile(
                            entry.getKey(), entry.getValue().sign());
                }
            }
        }
        List<GroupedRegionProfile> grouped = settings.grouped();
        if (grouped != null) {
            for (GroupedRegionProfile group : grouped) {
                Map<RegionState, RegionProfileService.FlagProfile> stateProfiles = new HashMap<>();
                Map<RegionState, io.github.md5sha256.realty.settings.SignProfile> signProfiles = new HashMap<>();
                for (Map.Entry<RegionState, RegionProfile> entry : group.states().entrySet()) {
                    stateProfiles.put(entry.getKey(),
                            new RegionProfileService.FlagProfile(
                                    entry.getValue().priority(), entry.getValue().flags()));
                    if (entry.getValue().sign() != null) {
                        signProfiles.put(entry.getKey(), entry.getValue().sign());
                    }
                }
                this.regionProfileService.addGroupedFlagProfile(group.regions(), stateProfiles);
                if (!signProfiles.isEmpty()) {
                    this.regionProfileService.addGroupedSignProfile(group.regions(), signProfiles);
                }
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
            @NotNull RealtyPaperApi paperApi,
            @NotNull ExecutorState executorState,
            @NotNull MessageContainer messageContainer,
            @NotNull NotificationService notificationService,
            @NotNull SafeLocationFinder safeLocationFinder
    ) {
        String version = getPluginMeta().getVersion();
        var helpCommand = new HelpCommand(messageContainer);
        List<CustomCommandBean> commands = List.of(
                new VersionCommand(version),
                new AddCommand(messageContainer),
                new AgentInviteCommand(paperApi, notificationService, messageContainer),
                new AgentInviteAcceptCommand(paperApi, notificationService, messageContainer),
                new AgentInviteRejectCommand(paperApi, notificationService, messageContainer),
                new AgentInviteWithdrawCommand(paperApi, notificationService, messageContainer),
                new AgentRemoveCommand(paperApi, notificationService, messageContainer),
                new AuctionCommandGroup(paperApi,
                        notificationService,
                        this.settings,
                        messageContainer),
                new BuyCommand(paperApi, notificationService, messageContainer),
                new CreateCommand(paperApi, this.settings, messageContainer),
                new RegisterCommand(paperApi, this.settings, messageContainer),
                new DeleteCommand(paperApi, messageContainer),
                new HistoryCommand(paperApi, this.settings, messageContainer),
                new InfoCommand(paperApi, this.settings, messageContainer),
                new ListCommand(paperApi, messageContainer),
                new OfferCommandGroup(paperApi,
                        notificationService,
                        messageContainer),
                new ExtendCommand(paperApi, messageContainer),
                new RentCommand(paperApi, notificationService, messageContainer),
                new UnrentCommand(paperApi, notificationService, messageContainer),
                new SetCommandGroup(paperApi, messageContainer),
                new UnsetCommandGroup(paperApi, messageContainer),
                new ReloadCommand(executorState, () -> {
                    performReload();
                    return null;
                }, messageContainer),
                new RemoveCommand(messageContainer),
                new SignCommand(paperApi, executorState, messageContainer),
                new TeleportCommand(paperApi, messageContainer, safeLocationFinder),
                new SubregionCommandGroup(paperApi, this.settings, messageContainer)
        );

        var manager = PaperCommandManager.builder(PaperSimpleSenderMapper.simpleSenderMapper())
                .executionCoordinator(ExecutionCoordinator.simpleCoordinator())
                .buildOnEnable(this);
        manager.brigadierManager().setNativeNumberSuggestions(true);
        Command.Builder<Source> rootBuilder = manager.commandBuilder("realty", "rl");
        // Register help commands and proxy the root literal to the base help command
        List<Command<? extends Source>> helpCommands = helpCommand.commands(rootBuilder);
        for (Command<? extends Source> cmd : helpCommands) {
            manager.command(cmd);
        }
        manager.command(rootBuilder.proxies(helpCommands.getFirst()));
        for (CustomCommandBean bean : commands) {
            for (Command<? extends Source> cmd : bean.commands(rootBuilder)) {
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
                    getLogger().severe("Failed to copy default resource: " + fileName);
                } else {
                    inputStream.transferTo(fileOutputStream);
                }
            }
        }
        YamlConfigurationLoader existingLoader = yamlLoader()
                .file(file)
                .build();
        ConfigurationNode existingRoot = existingLoader.load();
        try (InputStream defaultStream = getResource(fileName)) {
            if (defaultStream != null) {
                YamlConfigurationLoader defaultsLoader = yamlLoader()
                        .source(() -> new BufferedReader(
                                new InputStreamReader(defaultStream, StandardCharsets.UTF_8)))
                        .build();
                ConfigurationNode defaultsRoot = defaultsLoader.load();
                existingRoot.mergeFrom(defaultsRoot);
                existingLoader.save(existingRoot);
            }
        }
        return existingRoot;
    }


    private YamlConfigurationLoader.Builder yamlLoader() {
        return YamlConfigurationLoader.builder()
                .defaultOptions(options -> options.serializers(builder -> builder
                        .register(Component.class, ComponentSerializer.MINI_MESSAGE)
                        .register(SimpleDateFormat.class, SimpleDateFormatSerializer.INSTANCE)))
                .nodeStyle(NodeStyle.BLOCK);
    }
}
