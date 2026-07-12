package io.github.md5sha256.realty;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import io.github.md5sha256.realty.api.CurrencyFormatter;
import io.github.md5sha256.realty.api.ExecutorState;
import io.github.md5sha256.realty.api.NotificationService;
import io.github.md5sha256.realty.api.ProfileApplicator;
import io.github.md5sha256.realty.api.RealtyBackend;
import io.github.md5sha256.realty.api.RealtyPaperApi;
import io.github.md5sha256.realty.api.RealtyPaperApiImpl;
import io.github.md5sha256.realty.api.RegionProfileService;
import io.github.md5sha256.realty.api.RegionState;
import io.github.md5sha256.realty.api.SignCache;
import io.github.md5sha256.realty.api.SignProfile;
import io.github.md5sha256.realty.api.SignTextApplicator;
import io.github.md5sha256.realty.api.WorldGuardRegion;
import io.github.md5sha256.realty.api.event.AuctionEndedEvent;
import io.github.md5sha256.realty.api.event.LeaseExpiredEvent;
import io.github.md5sha256.realty.api.event.LeaseTerminatedEvent;
import io.github.md5sha256.realty.command.AddCommand;
import io.github.md5sha256.realty.command.AgentInviteAcceptCommand;
import io.github.md5sha256.realty.command.AgentInviteCommand;
import io.github.md5sha256.realty.command.AgentInviteRejectCommand;
import io.github.md5sha256.realty.command.AgentInviteWithdrawCommand;
import io.github.md5sha256.realty.command.AgentRemoveCommand;
import io.github.md5sha256.realty.command.AuctionCommandGroup;
import io.github.md5sha256.realty.command.BuyCommand;
import io.github.md5sha256.realty.command.CleanupCommandGroup;
import io.github.md5sha256.realty.command.CreateCommand;
import io.github.md5sha256.realty.command.CustomCommandBean;
import io.github.md5sha256.realty.command.DeleteCommand;
import io.github.md5sha256.realty.command.ExtendCommand;
import io.github.md5sha256.realty.command.HelpCommand;
import io.github.md5sha256.realty.command.HistoryCommand;
import io.github.md5sha256.realty.command.InfoCommand;
import io.github.md5sha256.realty.command.ListCommand;
import io.github.md5sha256.realty.command.OfferCommandGroup;
import io.github.md5sha256.realty.command.RegisterCommand;
import io.github.md5sha256.realty.command.ReloadCommand;
import io.github.md5sha256.realty.command.RemoveCommand;
import io.github.md5sha256.realty.command.RentCommand;
import io.github.md5sha256.realty.command.RentableCommand;
import io.github.md5sha256.realty.command.SearchCommand;
import io.github.md5sha256.realty.command.SearchDialog;
import io.github.md5sha256.realty.command.ModifyCommandGroup;
import io.github.md5sha256.realty.command.SetCommandGroup;
import io.github.md5sha256.realty.command.TerminateCommand;
import io.github.md5sha256.realty.command.SignCommand;
import io.github.md5sha256.realty.command.SubregionCommandGroup;
import io.github.md5sha256.realty.command.TagCommandGroup;
import io.github.md5sha256.realty.command.TeleportCommand;
import io.github.md5sha256.realty.command.TransferCommand;
import io.github.md5sha256.realty.command.UnrentCommand;
import io.github.md5sha256.realty.command.UnsetCommandGroup;
import io.github.md5sha256.realty.command.VersionCommand;
import io.github.md5sha256.realty.command.util.SafeLocationFinder;
import io.github.md5sha256.realty.database.Database;
import io.github.md5sha256.realty.database.RealtyBackendImpl;
import io.github.md5sha256.realty.database.SqlSessionWrapper;
import io.github.md5sha256.realty.database.maria.MariaDatabase;
import io.github.md5sha256.realty.event.RealtyEventDispatch;
import io.github.md5sha256.realty.listener.PropertyTaxListener;
import io.github.md5sha256.realty.listener.RegionNotificationListener;
import io.github.md5sha256.realty.listener.SignInteractionListener;
import io.github.md5sha256.realty.listener.SubregionWandListener;
import io.github.md5sha256.realty.command.SubregionDialog;
import io.github.md5sha256.realty.wand.SubregionWand;
import io.github.md5sha256.realty.wand.SubregionWandManager;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.localisation.MessageKeys;
import io.github.md5sha256.realty.settings.ConfigRegionTag;
import io.github.md5sha256.realty.settings.GroupedRegionProfile;
import io.github.md5sha256.realty.settings.RealtyTags;
import io.github.md5sha256.realty.settings.RegionProfile;
import io.github.md5sha256.realty.settings.RegionProfileSettings;
import io.github.md5sha256.realty.settings.RegionTagSettings;
import io.github.md5sha256.realty.settings.Settings;
import io.github.md5sha256.realty.settings.TaxSettings;
import io.github.md5sha256.realty.util.ComponentSerializer;
import io.github.md5sha256.realty.util.DateFormatter;
import io.github.md5sha256.realty.util.EssentialsNotificationService;
import io.github.md5sha256.realty.util.EssentialsSafeBlockPredicate;
import io.github.md5sha256.realty.util.SimpleDateFormatSerializer;
import io.github.md5sha256.realty.util.SquirrelIdUsernameResolver;
import io.github.md5sha256.realty.util.TransientNotificationService;
import io.papermc.paper.util.Tick;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import io.github.md5sha256.realty.economy.EconomyProvider;
import io.github.md5sha256.realty.economy.TreasuryEconomyProvider;
import io.github.md5sha256.realty.economy.VaultEconomyProvider;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.World;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.incendo.cloud.Command;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.paper.PaperCommandManager;
import org.incendo.cloud.paper.util.sender.PaperSimpleSenderMapper;
import org.incendo.cloud.paper.util.sender.Source;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class Realty extends JavaPlugin {

    private final MessageContainer messageContainer = new MessageContainer();
    private final AtomicReference<Settings> settings = new AtomicReference<>();
    private final AtomicReference<RegionProfileSettings> regionFlagSettings = new AtomicReference<>();
    private final AtomicReference<RealtyTags> realtyTags = new AtomicReference<>();
    private final AtomicReference<TaxSettings> taxSettings = new AtomicReference<>();
    private final RegionProfileService regionProfileService = new RegionProfileService(getLogger());
    private final SignCache signCache = new SignCache();
    private EconomyProvider economyProvider;
    private SquirrelIdUsernameResolver nameResolver;
    private ExecutorState executorState;
    private RealtyBackend logic;
    private ProfileApplicator profileApplicator;
    private DatabaseSettings databaseSettings;
    private NotificationService notificationService;
    private Database database;
    private SignTextApplicator signTextApplicator;
    private RealtyPaperApi paperApi;
    private RealtyEventDispatch eventDispatch;
    private boolean failedLoad = false;

    private static @NotNull PermissionDefault toBukkitPermission(@NotNull ConfigRegionTag tag) {
        if (tag.permission() == null) {
            throw new IllegalArgumentException("tag has a null permission");
        }
        return switch (tag.permission().permissionDefault()) {
            case OP -> PermissionDefault.OP;
            case TRUE -> PermissionDefault.TRUE;
            case FALSE -> PermissionDefault.FALSE;
        };
    }

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

    public RealtyTags realtyTags() {
        return this.realtyTags.get();
    }

    public TaxSettings taxSettings() {
        return this.taxSettings.get();
    }

    @Override
    public void onLoad() {
        try {
            initDataFolder();
            copyResourceTemplate("messages.yml", "defaults/default-messages.yml");
            copyResourceTemplate("settings.yml", "defaults/default-settings.yml");
            copyResourceTemplate("profiles.yml", "defaults/default-profiles.yml");
            copyResourceTemplate("taxes.yml", "defaults/default-taxes.yml");
            reloadMessages();
            this.databaseSettings = loadDatabaseSettings();
            this.settings.set(loadSettings());
            this.regionFlagSettings.set(loadRegionFlagSettings());
            this.realtyTags.set(new RealtyTags(loadRegionTagSettings()));
            this.taxSettings.set(loadTaxSettings());
            registerTagPermissions(this.realtyTags.get());
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
                .getMainThreadExecutor(this),
                Executors.newFixedThreadPool(4, threadFactory),
                Executors.newThreadPerTaskExecutor(threadFactory));
        try {
            this.nameResolver = new SquirrelIdUsernameResolver(
                    new File(getDataFolder(), "profiles.sqlite"),
                    this.executorState.networkExec());
        } catch (IOException ex) {
            getLogger().severe("Failed to initialize profile cache!");
            ex.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
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
        this.logic = new RealtyBackendImpl(mariaDatabase,
                this.nameResolver::getUsername,
                dateTime -> DateFormatter.format(this.settings.get(), dateTime),
                () -> this.settings.get().offerPaymentDurationSeconds());
        EconomyProvider economyProvider = resolveEconomyProvider();
        this.economyProvider = economyProvider;
        if (economyProvider == null) {
            getLogger().severe("No economy found (neither Treasury nor Vault), plugin will now disable!");
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
        if (getServer().getPluginManager().isPluginEnabled("Treasury")) {
            registerTreasuryTaxProvider();
        }
        this.paperApi = new RealtyPaperApiImpl(
                this.logic, economyProvider, this.executorState, this.database,
                this.regionProfileService, this.signTextApplicator, this.signCache,
                () -> this.settings.get().terminationNoticeSeconds());
        this.eventDispatch = new RealtyEventDispatch(
                getServer(),
                this.executorState.mainThreadExec(),
                task -> getServer().getScheduler().runTaskAsynchronously(this, task));
        scheduleTasks();
        registerCommands(this.paperApi,
                this.executorState,
                this.messageContainer,
                this.notificationService,
                safeLocationFinder);
        getServer().getServicesManager()
                .register(RealtyBackend.class, this.logic, this, ServicePriority.Normal);
        getServer().getServicesManager()
                .register(RealtyPaperApi.class, this.paperApi, this, ServicePriority.Normal);
        warnOrphanedTags();
        getLogger().info("Plugin enabled successfully");
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        if (this.profileApplicator != null) {
            this.profileApplicator.cancel();
        }
        if (this.executorState != null) {
            try (ExecutorService dbService = this.executorState.dbExec();
                 ExecutorService networkService = this.executorState.networkExec()) {
                dbService.shutdownNow();
                networkService.shutdownNow();
                if (!dbService.awaitTermination(30, TimeUnit.SECONDS)) {
                    getLogger().severe("Failed to await database threadpool shutdown!");
                }
                if (!networkService.awaitTermination(30, TimeUnit.SECONDS)) {
                    getLogger().severe("Failed to await network threadpool shutdown!");
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

    private void registerTreasuryTaxProvider() {
        var treasuryRegistration = getServer().getServicesManager()
                .getRegistration(net.democracycraft.treasury.api.TreasuryApi.class);
        if (treasuryRegistration != null) {
            getServer().getPluginManager().registerEvents(
                    new PropertyTaxListener(this.database, treasuryRegistration.getProvider(),
                            this.taxSettings, getLogger()), this);
            getLogger().info("Registered property tax listener (daily cycle)");
        }
    }

    private @Nullable EconomyProvider resolveEconomyProvider() {
        if (getServer().getPluginManager().isPluginEnabled("Treasury")) {
            var registration = getServer().getServicesManager()
                    .getRegistration(net.democracycraft.treasury.api.TreasuryApi.class);
            if (registration != null) {
                getLogger().info("Detected Treasury, using Treasury as the economy provider (full ledger support)");
                return new TreasuryEconomyProvider(registration.getProvider());
            }
            getLogger().warning("Treasury plugin is loaded but TreasuryApi service is not registered; falling back to Vault");
        }
        var registration = getServer().getServicesManager().getRegistration(Economy.class);
        if (registration != null) {
            getLogger().info("Using Vault as the economy provider");
            return new VaultEconomyProvider(registration.getProvider());
        }
        return null;
    }

    private void scheduleTasks() {
        BukkitScheduler scheduler = getServer().getScheduler();
        long intervalTicks = Tick.tick().fromDuration(Duration.ofMinutes(1));
        scheduler.runTaskTimerAsynchronously(this, () -> {
            if (this.logic == null) {
                return;
            }
            List<RealtyBackend.ExpiredBiddingAuction> endedAuctions = this.logic.clearExpiredBiddingAuctions();
            for (RealtyBackend.ExpiredBiddingAuction auction : endedAuctions) {
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
            if (!endedAuctions.isEmpty()) {
                // Resolve WorldGuard regions and fire post-events on the main thread.
                scheduler.runTask(this, () -> {
                    for (RealtyBackend.ExpiredBiddingAuction auction : endedAuctions) {
                        World world = getServer().getWorld(auction.worldId());
                        if (world == null) {
                            continue;
                        }
                        RegionManager regionManager = WorldGuard.getInstance().getPlatform()
                                .getRegionContainer().get(BukkitAdapter.adapt(world));
                        if (regionManager == null) {
                            continue;
                        }
                        ProtectedRegion protectedRegion = regionManager.getRegion(auction.worldGuardRegionId());
                        if (protectedRegion != null) {
                            this.eventDispatch.fireSync(new AuctionEndedEvent(
                                    new WorldGuardRegion(protectedRegion, world),
                                    auction.winnerId(), auction.auctioneerId()));
                        }
                    }
                });
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
                            this.logic.getRegionPlaceholders(expired.worldGuardRegionId(),
                                    expired.worldId()));
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
                                    WorldGuardRegion wgRegion = new WorldGuardRegion(protectedRegion, world);
                                    regionProfileService.applyFlags(
                                            wgRegion,
                                            RegionState.FOR_LEASE,
                                            leaseholdPlaceholders.getOrDefault(expired.worldGuardRegionId(),
                                                    Map.of()));
                                    // Post-event; RegionNotificationListener notifies tenant + landlord.
                                    this.eventDispatch.fireSync(new LeaseExpiredEvent(
                                            wgRegion, expired.tenantId(), expired.landlordId()));
                                }
                            }
                        }
                    }
                });
            }
            // Leaseholds whose scheduled termination date has elapsed: end them, refund any
            // prepaid-but-unused time (landlord → tenant), and notify both parties.
            List<RealtyBackend.TerminatedLeasehold> terminatedLeaseholds = this.logic.clearTerminatedLeaseholds();
            if (!terminatedLeaseholds.isEmpty()) {
                Map<String, Map<String, String>> terminatedPlaceholders = new HashMap<>();
                for (RealtyBackend.TerminatedLeasehold terminated : terminatedLeaseholds) {
                    terminatedPlaceholders.put(terminated.worldGuardRegionId(),
                            this.logic.getRegionPlaceholders(terminated.worldGuardRegionId(),
                                    terminated.worldId()));
                }
                scheduler.runTask(this, () -> {
                    for (RealtyBackend.TerminatedLeasehold terminated : terminatedLeaseholds) {
                        if (terminated.refund() > 0 && this.economyProvider != null) {
                            this.economyProvider.transfer(terminated.landlordId(), terminated.tenantId(),
                                    terminated.refund(), "Lease Termination Refund: " + terminated.worldGuardRegionId());
                        }
                        World world = getServer().getWorld(terminated.worldId());
                        if (world != null) {
                            RegionManager regionManager = WorldGuard.getInstance()
                                    .getPlatform()
                                    .getRegionContainer()
                                    .get(BukkitAdapter.adapt(world));
                            if (regionManager != null) {
                                ProtectedRegion protectedRegion = regionManager.getRegion(terminated.worldGuardRegionId());
                                if (protectedRegion != null) {
                                    protectedRegion.getOwners().removePlayer(terminated.tenantId());
                                    WorldGuardRegion wgRegion = new WorldGuardRegion(protectedRegion, world);
                                    regionProfileService.applyFlags(wgRegion, RegionState.FOR_LEASE,
                                            terminatedPlaceholders.getOrDefault(terminated.worldGuardRegionId(),
                                                    Map.of()));
                                    this.eventDispatch.fireSync(new LeaseTerminatedEvent(wgRegion,
                                            terminated.tenantId(), terminated.landlordId(),
                                            terminated.refund(), terminated.terminatedByRole()));
                                }
                            }
                        }
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

    private RegionTagSettings loadRegionTagSettings() throws IOException {
        ConfigurationNode settingsRoot = copyDefaultsYaml("region-tags");
        return settingsRoot.get(RegionTagSettings.class);
    }

    private TaxSettings loadTaxSettings() throws IOException {
        ConfigurationNode settingsRoot = copyDefaultsYaml("taxes");
        return settingsRoot.get(TaxSettings.class);
    }

    private void unregisterTagPermissions(@NotNull RealtyTags realtyTags) {
        PluginManager pluginManager = getServer().getPluginManager();
        for (ConfigRegionTag tag : realtyTags.values()) {
            if (tag.permission() != null) {
                pluginManager.removePermission(tag.permission().node());
            }
        }
    }

    private void registerTagPermissions(@NotNull RealtyTags realtyTags) {
        PluginManager pluginManager = getServer().getPluginManager();
        for (ConfigRegionTag tag : realtyTags.values()) {
            if (tag.permission() == null) {
                continue;
            }
            PermissionDefault bukkitPermission = toBukkitPermission(tag);
            try {
                pluginManager.addPermission(new Permission(tag.permission().node(),
                        bukkitPermission));
            } catch (IllegalArgumentException ex) {
                getLogger().warning("Failed to register tag permission because it already exists: " + tag.permission()
                        .node());
            }
        }
    }

    private void warnOrphanedTags() {
        executorState.dbExec().execute(() -> {
            try (SqlSessionWrapper session = database.openSession(true)) {
                List<String> dbTagIds = session.regionTagMapper().selectDistinctTagIds();
                Set<String> configTagIds = realtyTags.get().tagIds();
                List<String> orphaned = dbTagIds.stream()
                        .filter(tagId -> !configTagIds.contains(tagId))
                        .toList();
                if (!orphaned.isEmpty()) {
                    getLogger().warning(
                            "Found orphaned tags in the database that are not in region-tags.yml: "
                                    + String.join(", ", orphaned)
                                    + ". Run /realty cleanup tags to remove them.");
                }
            } catch (Exception ex) {
                getLogger().warning("Failed to check for orphaned tags: " + ex.getMessage());
            }
        });
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
                Map<RegionState, SignProfile> signProfiles = new HashMap<>();
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
        unregisterTagPermissions(this.realtyTags.get());
        this.realtyTags.set(new RealtyTags(loadRegionTagSettings()));
        registerTagPermissions(this.realtyTags.get());
        configureRegionFlagService(this.regionFlagSettings.get());
        this.profileApplicator.applyAll(this.settings.get().profileReapplyPerTick());
        this.taxSettings.set(loadTaxSettings());
        reloadMessages();
        warnOrphanedTags();
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

        SubregionWand subregionWand = new SubregionWand(this, this.settings);
        SubregionWandManager subregionWandManager = new SubregionWandManager();
        SubregionDialog subregionDialog = new SubregionDialog(paperApi, executorState,
                this.database, subregionWandManager, this.settings, this.realtyTags,
                messageContainer);
        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(
                new SubregionWandListener(this, subregionWand, subregionWandManager,
                        messageContainer), this);
        pluginManager.registerEvents(
                new RegionNotificationListener(notificationService, messageContainer), this);

        List<CustomCommandBean> commands = List.of(
                new VersionCommand(version),
                new AddCommand(messageContainer),
                new AgentInviteCommand(paperApi, notificationService, messageContainer, this.eventDispatch),
                new AgentInviteAcceptCommand(paperApi, notificationService, messageContainer, this.eventDispatch),
                new AgentInviteRejectCommand(paperApi, notificationService, messageContainer, this.eventDispatch),
                new AgentInviteWithdrawCommand(paperApi, notificationService, messageContainer, this.eventDispatch),
                new AgentRemoveCommand(paperApi, notificationService, messageContainer, this.eventDispatch),
                new AuctionCommandGroup(paperApi,
                        notificationService,
                        this.settings,
                        messageContainer,
                        this.eventDispatch),
                new BuyCommand(paperApi, messageContainer, this.eventDispatch),
                new CreateCommand(paperApi, this.settings, messageContainer, this.eventDispatch),
                new RegisterCommand(paperApi, this.settings, messageContainer, this.eventDispatch),
                new DeleteCommand(paperApi, messageContainer, this.eventDispatch),
                new HistoryCommand(paperApi, this.settings, messageContainer),
                new InfoCommand(paperApi,
                        this.settings,
                        this.database,
                        this.realtyTags,
                        messageContainer),
                new ListCommand(paperApi, messageContainer),
                new OfferCommandGroup(paperApi,
                        notificationService,
                        messageContainer,
                        this.eventDispatch),
                new ExtendCommand(paperApi, messageContainer, this.eventDispatch),
                new RentCommand(paperApi, messageContainer, this.eventDispatch),
                new RentableCommand(paperApi, messageContainer),
                new UnrentCommand(paperApi, messageContainer, this.eventDispatch),
                new SetCommandGroup(paperApi, messageContainer, this.eventDispatch),
                new ModifyCommandGroup(paperApi, messageContainer, this.eventDispatch),
                new TerminateCommand(paperApi, messageContainer, this.eventDispatch),
                new TransferCommand(paperApi, messageContainer, this.eventDispatch),
                new UnsetCommandGroup(paperApi, messageContainer),
                new ReloadCommand(executorState, () -> {
                    performReload();
                    return null;
                }, messageContainer),
                new RemoveCommand(messageContainer),
                new SignCommand(paperApi, executorState, messageContainer),
                new TeleportCommand(getLogger(), paperApi, this.settings, messageContainer, safeLocationFinder),
                new SubregionCommandGroup(subregionWand, subregionWandManager, subregionDialog,
                        messageContainer),
                new CleanupCommandGroup(this.database,
                        executorState,
                        this.realtyTags,
                        messageContainer),
                new TagCommandGroup(this.database,
                        executorState,
                        this.realtyTags,
                        messageContainer),
                new SearchCommand(
                        new SearchDialog(this.database, executorState,
                                this.realtyTags, messageContainer),
                        messageContainer)
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
