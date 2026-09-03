package io.github.md5sha256.realty.adapter.query;

import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionType;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A region's footprint and vertical bounds. For a cuboid the points are the four corners of its
 * footprint so a consumer can treat both shapes uniformly. Area and volume are derivable and
 * deliberately not sent.
 */
public record RegionDimensions(@NotNull String shape,
                               int minY,
                               int maxY,
                               @NotNull List<Point> points) {

    public record Point(int x, int z) {
    }

    /** Must be called on the main thread: {@link ProtectedRegion} is not thread-safe. */
    public static @NotNull RegionDimensions fromProtectedRegion(@NotNull ProtectedRegion region) {
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();
        if (region.getType() == RegionType.CUBOID) {
            return new RegionDimensions("CUBOID", min.y(), max.y(), List.of(
                    new Point(min.x(), min.z()),
                    new Point(max.x(), min.z()),
                    new Point(max.x(), max.z()),
                    new Point(min.x(), max.z())));
        }
        List<Point> points = region.getPoints().stream()
                .map(p -> new Point(p.x(), p.z()))
                .toList();
        return new RegionDimensions("POLYGONAL", min.y(), max.y(), points);
    }
}
