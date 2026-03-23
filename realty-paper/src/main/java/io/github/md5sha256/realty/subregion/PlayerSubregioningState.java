package io.github.md5sha256.realty.subregion;

import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Polygonal2DRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.SessionManager;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import io.github.md5sha256.realty.command.util.WorldGuardRegion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

public class PlayerSubregioningState {

    public sealed interface SelectionResult {
        record Success(@NotNull Region selection) implements SelectionResult {}
        record WrongWorld() implements SelectionResult {}
        record IncompleteSelection() implements SelectionResult {}
        record ExceedsParentBounds() implements SelectionResult {}
        record NoRegionManager() implements SelectionResult {}
        record OverlapsSibling(@NotNull ProtectedRegion sibling) implements SelectionResult {}
    }

    private final Player player;
    private final WorldGuardRegion parentRegion;

    public PlayerSubregioningState(@NotNull Player player, @NotNull WorldGuardRegion parentRegion) {
        this.player = player;
        this.parentRegion = parentRegion;
    }

    public @NotNull WorldGuardRegion parentRegion() {
        return parentRegion;
    }

    private static boolean checkCuboidFacesContained(ProtectedRegion region,
                                                     BlockVector3 min,
                                                     BlockVector3 max) {
        // Check 2 faces with fixed X
        for (int y = min.y(); y <= max.y(); y++) {
            for (int z = min.z(); z <= max.z(); z++) {
                if (!region.contains(min.x(), y, z) || !region.contains(max.x(), y, z)) {
                    return false;
                }
            }
        }
        // Check 2 faces with fixed Y
        for (int x = min.x(); x <= max.x(); x++) {
            for (int z = min.z(); z <= max.z(); z++) {
                if (!region.contains(x, min.y(), z) || !region.contains(x, max.y(), z)) {
                    return false;
                }
            }
        }
        // Check 2 faces with fixed Z
        for (int x = min.x(); x <= max.x(); x++) {
            for (int y = min.y(); y <= max.y(); y++) {
                if (!region.contains(x, y, min.z()) || !region.contains(x, y, max.z())) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean regionIsFullyContainedByParent(@NotNull Region region,
                                                          @NotNull ProtectedRegion parent) {
        if (region instanceof CuboidRegion cuboid) {
            return checkCuboidFacesContained(parent,
                    cuboid.getMinimumPoint(),
                    cuboid.getMaximumPoint());
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

    private static boolean checkPolygonSurfaceContained(ProtectedRegion parent,
                                                        Polygonal2DRegion polygon) {
        List<BlockVector2> points = polygon.getPoints();
        int minY = polygon.getMinimumY();
        int maxY = polygon.getMaximumY();
        BlockVector3 min = polygon.getMinimumPoint();
        BlockVector3 max = polygon.getMaximumPoint();

        // Check top and bottom faces: iterate bounding box, filter by polygon containment
        for (int x = min.x(); x <= max.x(); x++) {
            for (int z = min.z(); z <= max.z(); z++) {
                if (polygon.contains(BlockVector3.at(x, minY, z))) {
                    if (!parent.contains(x, minY, z) || !parent.contains(x, maxY, z)) {
                        return false;
                    }
                }
            }
        }

        // Check side walls: walk each polygon edge with Bresenham, sweep vertically
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

    public @NotNull SelectionResult tryApplySelection() {
        SessionManager manager = WorldEdit.getInstance().getSessionManager();
        LocalSession localSession = manager.get(BukkitAdapter.adapt(this.player));
        if (!Objects.equals(localSession.getSelectionWorld(),
                BukkitAdapter.adapt(parentRegion.world()))) {
            return new SelectionResult.WrongWorld();
        }
        Region selection;
        try {
            selection = localSession.getSelection().clone();
        } catch (IncompleteRegionException ex) {
            return new SelectionResult.IncompleteSelection();
        }
        ProtectedRegion parent = parentRegion.region();
        if (!regionIsFullyContainedByParent(selection, parent)) {
            return new SelectionResult.ExceedsParentBounds();
        }
        RegionManager regionManager = WorldGuard.getInstance()
                .getPlatform()
                .getRegionContainer()
                .get(BukkitAdapter.adapt(parentRegion.world()));
        if (regionManager == null) {
            return new SelectionResult.NoRegionManager();
        }
        for (ProtectedRegion sibling : regionManager.getRegions().values()) {
            if (!Objects.equals(sibling.getParent(), parent)) {
                continue;
            }
            for (BlockVector3 point : selection) {
                if (sibling.contains(point)) {
                    return new SelectionResult.OverlapsSibling(sibling);
                }
            }
        }
        return new SelectionResult.Success(selection);
    }

}
