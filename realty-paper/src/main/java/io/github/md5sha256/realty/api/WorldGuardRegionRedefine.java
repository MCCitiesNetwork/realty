package io.github.md5sha256.realty.api;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Polygonal2DRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.managers.storage.StorageException;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedPolygonalRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import io.github.md5sha256.realty.command.util.SubregionSelectionValidator;
import org.jetbrains.annotations.NotNull;

import java.util.List;
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

    /** Why a new shape is refused. */
    public sealed interface Problem {

        record TooSmall(long volume, int minVolume) implements Problem {
        }

        record OutsideParent(@NotNull String parentId) implements Problem {
        }

        record OverlapsSibling(@NotNull String siblingId) implements Problem {
        }

        record ChildOutside(@NotNull String childId) implements Problem {
        }

        /** The region's world has no region manager. */
        record NoRegionManager() implements Problem {
        }
    }

    public sealed interface Outcome {

        /** {@code region} holds the replacement, which is now in WorldGuard. */
        record Redefined(@NotNull WorldGuardRegion region) implements Outcome {
        }

        /**
         * Redefined, but WorldGuard could not save. The change still stands: the region manager
         * holds the replacement and WorldGuard writes it on its next save.
         */
        record RedefinedSaveFailed(@NotNull WorldGuardRegion region, @NotNull String error) implements Outcome {
        }
    }

    /**
     * A region with {@code id} and the selection's shape. Empty for a selection that is neither a
     * cuboid nor a polygon, the two shapes Realty creates.
     */
    public static @NotNull Optional<ProtectedRegion> fromSelection(@NotNull String id, @NotNull Region selection) {
        if (selection instanceof CuboidRegion cuboid) {
            return Optional.of(new ProtectedCuboidRegion(id, cuboid.getMinimumPoint(), cuboid.getMaximumPoint()));
        }
        if (selection instanceof Polygonal2DRegion polygon) {
            return Optional.of(new ProtectedPolygonalRegion(id, polygon.getPoints(),
                    polygon.getMinimumY(), polygon.getMaximumY()));
        }
        return Optional.empty();
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
     * Checks a new shape against the region's surroundings. With {@code subregion}, the shape must
     * have at least {@code minVolume} blocks, lie fully inside the region's parent, and not overlap
     * a sibling. Always, every child of the region must lie fully inside {@code replacement}.
     */
    public @NotNull Optional<Problem> check(@NotNull WorldGuardRegion region, @NotNull Region selection,
                                            @NotNull ProtectedRegion replacement, boolean subregion,
                                            int minVolume) {
        RegionManager regionManager = this.regionContainer.get(BukkitAdapter.adapt(region.world()));
        if (regionManager == null) {
            return Optional.of(new Problem.NoRegionManager());
        }
        ProtectedRegion old = region.region();
        ProtectedRegion parent = old.getParent();
        if (subregion && parent != null) {
            long volume = SubregionSelectionValidator.blockVolume(selection);
            if (volume < minVolume) {
                return Optional.of(new Problem.TooSmall(volume, minVolume));
            }
            if (!SubregionSelectionValidator.regionIsFullyContainedByParent(selection, parent)) {
                return Optional.of(new Problem.OutsideParent(parent.getId()));
            }
            ProtectedRegion sibling = SubregionSelectionValidator.overlappingSibling(
                    selection, parent, regionManager, old.getId());
            if (sibling != null) {
                return Optional.of(new Problem.OverlapsSibling(sibling.getId()));
            }
        }
        return SubregionSelectionValidator.firstChildOutside(old, replacement, regionManager)
                .map(child -> new Problem.ChildOutside(child.getId()));
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
        ProtectedRegion old = region.region();
        try {
            // Owners, members (players and groups), every flag, the priority and the parent.
            replacement.copyFrom(old);
            // The same id, so this replaces the old region in the manager and its index.
            regionManager.addRegion(replacement);
            // Children hold the parent object, not its id, so they still hold the old shape.
            for (ProtectedRegion child : List.copyOf(regionManager.getRegions().values())) {
                if (child.getParent() == old) {
                    child.setParent(replacement);
                }
            }
        } catch (ProtectedRegion.CircularInheritanceException ex) {
            // The replacement takes the old region's place in a tree that had no cycle.
            throw new IllegalStateException("Redefining " + old.getId() + " made a parent cycle", ex);
        }
        WorldGuardRegion redefined = new WorldGuardRegion(replacement, region.world());
        try {
            regionManager.save();
        } catch (StorageException ex) {
            this.logger.log(Level.WARNING, "Could not save the redefine of WorldGuard region " + old.getId(), ex);
            return new Outcome.RedefinedSaveFailed(redefined, String.valueOf(ex.getMessage()));
        }
        return new Outcome.Redefined(redefined);
    }
}
