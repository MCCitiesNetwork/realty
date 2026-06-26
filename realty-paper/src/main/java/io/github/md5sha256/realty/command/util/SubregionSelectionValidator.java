package io.github.md5sha256.realty.command.util;

import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Polygonal2DRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Geometry and containment checks shared by the subregion creation flow.
 *
 * <p>Extracted from {@code SubregionCommandGroup} so the wand + dialog flow can reuse the same
 * containment, overlap and parent-candidate logic that the old typed command used.</p>
 */
public final class SubregionSelectionValidator {

    private SubregionSelectionValidator() {
    }

    /**
     * Returns the parent regions the player may subregion under: regions that fully contain the
     * selection and (unless {@code canBypass}) are owned by the player. Freehold-contract and
     * tag-blacklist filtering happen later against the database — this method is pure WorldGuard
     * geometry and ownership.
     */
    public static @NotNull List<ProtectedRegion> candidateParents(@NotNull UUID playerId,
                                                                   boolean canBypass,
                                                                   @NotNull Region selection,
                                                                   @NotNull RegionManager regionManager) {
        List<ProtectedRegion> candidates = new ArrayList<>();
        for (ProtectedRegion region : regionManager.getRegions().values()) {
            if (!canBypass && !region.getOwners().contains(playerId)) {
                continue;
            }
            if (!regionIsFullyContainedByParent(selection, region)) {
                continue;
            }
            candidates.add(region);
        }
        return candidates;
    }

    /**
     * Returns the first sibling subregion (a child of {@code parent}) that the selection overlaps,
     * or {@code null} if the selection is clear.
     */
    public static @Nullable ProtectedRegion overlappingSibling(@NotNull Region selection,
                                                               @NotNull ProtectedRegion parent,
                                                               @NotNull RegionManager regionManager) {
        for (ProtectedRegion sibling : regionManager.getRegions().values()) {
            if (!Objects.equals(sibling.getParent(), parent)) {
                continue;
            }
            for (BlockVector3 point : selection) {
                if (sibling.contains(point)) {
                    return sibling;
                }
            }
        }
        return null;
    }

    public static boolean regionIsFullyContainedByParent(@NotNull Region region,
                                                         @NotNull ProtectedRegion parent) {
        if (region instanceof CuboidRegion cuboid) {
            return checkCuboidFacesContained(parent,
                    cuboid.getMinimumPoint(), cuboid.getMaximumPoint());
        } else if (region instanceof Polygonal2DRegion polygon) {
            return checkPolygonSurfaceContained(parent, polygon);
        }
        for (BlockVector3 point : region) {
            if (!parent.contains(point)) {
                return false;
            }
        }
        return true;
    }

    private static boolean checkCuboidFacesContained(ProtectedRegion region,
                                                     BlockVector3 min,
                                                     BlockVector3 max) {
        for (int y = min.y(); y <= max.y(); y++) {
            for (int z = min.z(); z <= max.z(); z++) {
                if (!region.contains(min.x(), y, z) || !region.contains(max.x(), y, z)) {
                    return false;
                }
            }
        }
        for (int x = min.x(); x <= max.x(); x++) {
            for (int z = min.z(); z <= max.z(); z++) {
                if (!region.contains(x, min.y(), z) || !region.contains(x, max.y(), z)) {
                    return false;
                }
            }
        }
        for (int x = min.x(); x <= max.x(); x++) {
            for (int y = min.y(); y <= max.y(); y++) {
                if (!region.contains(x, y, min.z()) || !region.contains(x, y, max.z())) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean checkPolygonSurfaceContained(ProtectedRegion parent,
                                                        Polygonal2DRegion polygon) {
        List<BlockVector2> points = polygon.getPoints();
        int minY = polygon.getMinimumY();
        int maxY = polygon.getMaximumY();
        BlockVector3 min = polygon.getMinimumPoint();
        BlockVector3 max = polygon.getMaximumPoint();

        for (int x = min.x(); x <= max.x(); x++) {
            for (int z = min.z(); z <= max.z(); z++) {
                if (polygon.contains(BlockVector3.at(x, minY, z))) {
                    if (!parent.contains(x, minY, z) || !parent.contains(x, maxY, z)) {
                        return false;
                    }
                }
            }
        }

        for (int i = 0; i < points.size(); i++) {
            BlockVector2 a = points.get(i);
            BlockVector2 b = points.get((i + 1) % points.size());
            int dx = Math.abs(b.x() - a.x());
            int dz = Math.abs(b.z() - a.z());
            int sx = a.x() < b.x() ? 1 : -1;
            int sz = a.z() < b.z() ? 1 : -1;
            int err = dx - dz;
            int cx = a.x();
            int cz = a.z();
            while (true) {
                for (int y = minY; y <= maxY; y++) {
                    if (!parent.contains(cx, y, cz)) {
                        return false;
                    }
                }
                if (cx == b.x() && cz == b.z()) {
                    break;
                }
                int e2 = 2 * err;
                if (e2 > -dz) {
                    err -= dz;
                    cx += sx;
                }
                if (e2 < dx) {
                    err += dx;
                    cz += sz;
                }
            }
        }
        return true;
    }
}
