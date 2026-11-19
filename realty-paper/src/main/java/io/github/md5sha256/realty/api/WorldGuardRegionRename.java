package io.github.md5sha256.realty.api;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.managers.storage.StorageException;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedPolygonalRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Renames a WorldGuard region. WorldGuard fixes a region's id for its lifetime, so a rename
 * replaces the region with a copy that has the new id. Used by the region rename. Call it on the
 * main thread.
 */
public final class WorldGuardRegionRename {

    private final RegionContainer regionContainer;
    private final Logger logger;

    public WorldGuardRegionRename(@NotNull RegionContainer regionContainer, @NotNull Logger logger) {
        this.regionContainer = regionContainer;
        this.logger = logger;
    }

    public sealed interface Outcome {

        /** {@code region} is the copy that replaced the region. */
        record Renamed(@NotNull WorldGuardRegion region) implements Outcome {
        }

        /**
         * Renamed, but WorldGuard could not save. The rename still stands: the region manager
         * holds the copy and WorldGuard writes it on its next save.
         */
        record RenamedSaveFailed(@NotNull WorldGuardRegion region, @NotNull String error) implements Outcome {
        }

        /** A region with the new id exists, or the world has no region manager. Nothing changed. */
        record NameTaken() implements Outcome {
        }
    }

    /**
     * @return whether the region can be rebuilt under another id: a cuboid or a polygon, the two
     *         shapes Realty creates
     */
    public static boolean isSupported(@NotNull ProtectedRegion region) {
        return region instanceof ProtectedCuboidRegion || region instanceof ProtectedPolygonalRegion;
    }

    /**
     * @return whether the region's world has no region with {@code newId}
     */
    public boolean isFree(@NotNull WorldGuardRegion region, @NotNull String newId) {
        RegionManager regionManager = this.regionContainer.get(BukkitAdapter.adapt(region.world()));
        return regionManager != null && !regionManager.hasRegion(newId);
    }

    /**
     * Replaces {@code region} with a copy under {@code newId}, points its child regions at the
     * copy, and saves. The region must be {@linkplain #isSupported supported}.
     */
    public @NotNull Outcome rename(@NotNull WorldGuardRegion region, @NotNull String newId) {
        RegionManager regionManager = this.regionContainer.get(BukkitAdapter.adapt(region.world()));
        if (regionManager == null || regionManager.hasRegion(newId)) {
            return new Outcome.NameTaken();
        }
        ProtectedRegion old = region.region();
        ProtectedRegion copy = sameShape(old, newId);
        // Before the old region goes: removing it unsets its children's parent.
        takePlace(regionManager, old, copy);
        regionManager.removeRegion(old.getId());
        WorldGuardRegion renamed = new WorldGuardRegion(copy, region.world());
        try {
            regionManager.save();
        } catch (StorageException ex) {
            this.logger.log(Level.WARNING, "Could not save the rename of WorldGuard region "
                    + old.getId() + " to " + newId, ex);
            return new Outcome.RenamedSaveFailed(renamed, String.valueOf(ex.getMessage()));
        }
        return new Outcome.Renamed(renamed);
    }

    /**
     * Puts {@code replacement} in the manager in {@code old}'s place: it takes the old region's
     * owners, members (players and groups), every flag, the priority and the parent, and every
     * child of the old region is pointed at it. The caller removes the old region if the ids differ.
     */
    static void takePlace(@NotNull RegionManager regionManager, @NotNull ProtectedRegion old,
                          @NotNull ProtectedRegion replacement) {
        try {
            replacement.copyFrom(old);
            regionManager.addRegion(replacement);
            // Children hold the parent object, so they still hold the old one.
            for (ProtectedRegion child : List.copyOf(regionManager.getRegions().values())) {
                if (old.equals(child.getParent())) {
                    child.setParent(replacement);
                }
            }
        } catch (ProtectedRegion.CircularInheritanceException ex) {
            // The replacement takes the old region's place in a tree that had no cycle.
            throw new IllegalStateException("Replacing " + old.getId() + " made a parent cycle", ex);
        }
    }

    private static @NotNull ProtectedRegion sameShape(@NotNull ProtectedRegion old, @NotNull String newId) {
        if (old instanceof ProtectedPolygonalRegion polygon) {
            return new ProtectedPolygonalRegion(newId, old.isTransient(), polygon.getPoints(),
                    old.getMinimumPoint().y(), old.getMaximumPoint().y());
        }
        return new ProtectedCuboidRegion(newId, old.isTransient(), old.getMinimumPoint(), old.getMaximumPoint());
    }
}
