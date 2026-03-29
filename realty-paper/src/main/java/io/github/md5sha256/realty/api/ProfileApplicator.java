package io.github.md5sha256.realty.api;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import io.github.md5sha256.realty.command.util.WorldGuardRegion;
import io.github.md5sha256.realty.api.RealtyApi;
import io.github.md5sha256.realty.database.entity.RealtyRegionEntity;
import io.github.md5sha256.realty.util.ExecutorState;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Manages the lifecycle of applying region flag profiles to all realty regions.
 * Supports cancelling an in-progress application (e.g. on reload) and starting a new one.
 */
public class ProfileApplicator {

    private final Plugin plugin;
    private final Logger logger;
    private final Server server;
    private final RegionProfileService regionProfileService;
    private final ExecutorState executorState;
    private final RealtyApi logic;
    private final SignTextApplicator signTextApplicator;
    private final SignCache signCache;
    private BukkitTask currentTask;

    public ProfileApplicator(@NotNull Plugin plugin,
                             @NotNull RegionProfileService regionProfileService,
                             @NotNull ExecutorState executorState,
                             @NotNull RealtyApi logic,
                             @NotNull SignTextApplicator signTextApplicator,
                             @NotNull SignCache signCache) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.server = plugin.getServer();
        this.regionProfileService = regionProfileService;
        this.executorState = executorState;
        this.logic = logic;
        this.signTextApplicator = signTextApplicator;
        this.signCache = signCache;
    }

    /**
     * Cancels any in-progress application task, then fetches all regions
     * from the database and applies profiles in batches of {@code perTick}
     * regions per server tick.
     *
     * @param perTick maximum number of regions to process per tick
     */
    public void applyAll(int perTick) {
        cancel();
        CompletableFuture.supplyAsync(() -> this.logic.getAllRegionsWithState(), this.executorState.dbExec())
                .thenAcceptAsync(regions -> {
                    if (regions.isEmpty()) {
                        return;
                    }
                    int[] index = {0};
                    BukkitTask[] taskHolder = {null};
                    taskHolder[0] = this.server.getScheduler().runTaskTimer(this.plugin, () -> {
                        int processed = 0;
                        while (index[0] < regions.size() && processed < perTick) {
                            RealtyApi.RegionWithState rws = regions.get(index[0]++);
                            RealtyRegionEntity entity = rws.region();
                            World world = this.server.getWorld(entity.worldId());
                            if (world == null) {
                                continue;
                            }
                            RegionManager regionManager = WorldGuard.getInstance()
                                    .getPlatform()
                                    .getRegionContainer()
                                    .get(BukkitAdapter.adapt(world));
                            if (regionManager == null) {
                                continue;
                            }
                            ProtectedRegion protectedRegion = regionManager.getRegion(entity.worldGuardRegionId());
                            if (protectedRegion == null) {
                                continue;
                            }
                            WorldGuardRegion wgRegion = new WorldGuardRegion(protectedRegion, world);
                            this.regionProfileService.clearAllFlags(wgRegion);
                            this.regionProfileService.applyFlags(wgRegion, rws.state(), rws.placeholders());
                            List<SignCache.BlockPosition> loadedSigns = this.signCache.getSignsByRegion(
                                    entity.worldGuardRegionId(), entity.worldId());
                            for (SignCache.BlockPosition pos : loadedSigns) {
                                this.signTextApplicator.applySignText(world,
                                        pos.x(), pos.y(), pos.z(),
                                        entity.worldGuardRegionId(), rws.state(), rws.placeholders());
                            }
                            processed++;
                        }
                        if (index[0] >= regions.size()) {
                            taskHolder[0].cancel();
                            this.currentTask = null;
                        }
                    }, 0L, 1L);
                    this.currentTask = taskHolder[0];
                }, this.executorState.mainThreadExec());
    }

    /**
     * Cancels the current profile application task, if one is running.
     */
    public void cancel() {
        if (this.currentTask != null && !this.currentTask.isCancelled()) {
            this.currentTask.cancel();
            this.currentTask = null;
            this.logger.info("Cancelled in-progress profile application");
        }
    }
}
