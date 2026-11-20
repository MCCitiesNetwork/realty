package io.github.md5sha256.realty.api;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.managers.storage.StorageException;
import com.sk89q.worldguard.protection.regions.ProtectedPolygonalRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import io.github.md5sha256.realty.api.RealtyPaperApi.RedefineRegionResult;
import io.github.md5sha256.realty.command.util.SubregionSelectionValidator;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Changes a WorldGuard region's shape. A region with the same id and the new shape replaces it and
 * takes everything else the old one had. Every method runs on the main thread: WorldGuard's region
 * tree is not thread-safe.
 */
public final class WorldGuardRegionRedefine {

    private final RegionContainer regionContainer;
    private final Logger logger;

    public WorldGuardRegionRedefine(@NotNull RegionContainer regionContainer, @NotNull Logger logger) {
        this.regionContainer = regionContainer;
        this.logger = logger;
    }

    /**
     * @param region    holds the replacement, which is now in WorldGuard
     * @param saveError set when WorldGuard could not save. The change still stands: the region
     *                  manager holds the replacement and WorldGuard writes it on its next save.
     */
    public record Outcome(@NotNull WorldGuardRegion region, @NotNull Optional<String> saveError) {
    }

    /**
     * Whether both regions have one shape: the same class, the same Y range, and the same corners
     * for a cuboid or the same points in the same order for a polygon.
     */
    public static boolean sameShape(@NotNull ProtectedRegion a, @NotNull ProtectedRegion b) {
        if (a.getClass() != b.getClass()
                || !a.getMinimumPoint().equals(b.getMinimumPoint())
                || !a.getMaximumPoint().equals(b.getMaximumPoint())) {
            return false;
        }
        return !(a instanceof ProtectedPolygonalRegion) || a.getPoints().equals(b.getPoints());
    }

    /**
     * Checks a new shape against the region's surroundings, and gives the refusal if it fails.
     * When the region has a WorldGuard parent, the shape must lie fully inside it and not overlap a
     * sibling; with {@code subregion} it must also have at least {@code minVolume} blocks. Always,
     * every child of the region must lie fully inside {@code replacement}.
     */
    public @NotNull Optional<RedefineRegionResult> check(@NotNull WorldGuardRegion region, @NotNull Region selection,
                                                         @NotNull ProtectedRegion replacement, boolean subregion,
                                                         int minVolume) {
        RegionManager regionManager = this.regionContainer.get(BukkitAdapter.adapt(region.world()));
        if (regionManager == null) {
            return Optional.of(new RedefineRegionResult.Error(
                    "WorldGuard has no region manager for " + region.world().getName()));
        }
        ProtectedRegion old = region.region();
        ProtectedRegion parent = old.getParent();
        if (parent != null) {
            // Only a subregion has a minimum volume; any region with a parent stays inside it and
            // clear of its siblings, as subregion create requires.
            long volume = SubregionSelectionValidator.blockVolume(selection);
            if (subregion && volume < minVolume) {
                return Optional.of(new RedefineRegionResult.TooSmall(volume, minVolume));
            }
            if (!SubregionSelectionValidator.regionIsFullyContainedByParent(selection, parent)) {
                return Optional.of(new RedefineRegionResult.OutsideParent(parent.getId()));
            }
            ProtectedRegion sibling = SubregionSelectionValidator.overlappingSibling(
                    selection, parent, regionManager, old.getId());
            if (sibling != null) {
                return Optional.of(new RedefineRegionResult.OverlapsSibling(sibling.getId()));
            }
        }
        return SubregionSelectionValidator.firstChildOutside(old, replacement, regionManager)
                .map(child -> new RedefineRegionResult.ChildOutside(child.getId()));
    }

    /**
     * Puts {@code replacement} in the region's place: it takes the old region's owners, members,
     * flags, priority and parent, and the old region's children are pointed at it. Then saves.
     * {@code replacement} must have the region's id.
     */
    public @NotNull Outcome redefine(@NotNull WorldGuardRegion region, @NotNull ProtectedRegion replacement) {
        RegionManager regionManager = this.regionContainer.get(BukkitAdapter.adapt(region.world()));
        if (regionManager == null) {
            throw new IllegalStateException("No region manager for the world of " + region.region().getId());
        }
        // The same id, so the replacement takes the old region's place in the manager and its index.
        WorldGuardRegionRename.takePlace(regionManager, region.region(), replacement);
        WorldGuardRegion redefined = new WorldGuardRegion(replacement, region.world());
        try {
            regionManager.save();
        } catch (StorageException ex) {
            this.logger.log(Level.WARNING, "Could not save the redefine of WorldGuard region "
                    + region.region().getId(), ex);
            return new Outcome(redefined, Optional.of(String.valueOf(ex.getMessage())));
        }
        return new Outcome(redefined, Optional.empty());
    }
}
