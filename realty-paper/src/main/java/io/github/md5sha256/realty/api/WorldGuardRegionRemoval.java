package io.github.md5sha256.realty.api;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.managers.storage.StorageException;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Removes a region whose Realty rows are already deleted from WorldGuard, saves the change, and
 * clears the region's Realty flags. Used by {@code /realty delete}, the subregion delete and the
 * scheduled subregion sweep. Call it on the main thread.
 */
public final class WorldGuardRegionRemoval {

    private final RegionContainer regionContainer;
    private final RegionProfileService regionProfileService;
    private final Logger logger;

    public WorldGuardRegionRemoval(@NotNull RegionContainer regionContainer,
                                   @NotNull RegionProfileService regionProfileService,
                                   @NotNull Logger logger) {
        this.regionContainer = regionContainer;
        this.regionProfileService = regionProfileService;
        this.logger = logger;
    }

    /**
     * Removes {@code region}, saves, and clears its flags.
     *
     * @return the WorldGuard save error, if the save failed; the flags are then left as they are
     */
    public @NotNull Optional<String> remove(@NotNull WorldGuardRegion region) {
        RegionManager regionManager = this.regionContainer.get(BukkitAdapter.adapt(region.world()));
        if (regionManager != null) {
            regionManager.removeRegion(region.region().getId());
            try {
                regionManager.save();
            } catch (StorageException ex) {
                this.logger.log(Level.WARNING, "Could not save the removal of WorldGuard region "
                        + region.region().getId(), ex);
                return Optional.of(String.valueOf(ex.getMessage()));
            }
        }
        this.regionProfileService.clearAllFlags(region);
        return Optional.empty();
    }
}
