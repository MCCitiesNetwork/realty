package io.github.md5sha256.realty.adapter.query;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionType;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * A region's footprint, vertical bounds and priority. For a cuboid the points are the four corners
 * of its footprint so a consumer can treat both shapes uniformly. Area and volume are derivable and
 * deliberately not sent.
 *
 * <p>The priority is WorldGuard's own, and is what settles overlapping regions: where two cover the
 * same block, the higher priority is the one whose rules apply. A consumer drawing a map needs it
 * for the same reason, since a plot inside a district has to be drawn over the district rather than
 * under it to be seen at all.</p>
 */
public record RegionDimensions(@NotNull String shape,
                               int minY,
                               int maxY,
                               int priority,
                               @NotNull List<Point> points) {

    public record Point(int x, int z) {
    }

    /** Must be called on the main thread: {@link ProtectedRegion} is not thread-safe. */
    public static @NotNull RegionDimensions fromProtectedRegion(@NotNull ProtectedRegion region) {
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();
        if (region.getType() == RegionType.CUBOID) {
            return new RegionDimensions("CUBOID", min.y(), max.y(), region.getPriority(), distinct(List.of(
                    new Point(min.x(), min.z()),
                    new Point(max.x(), min.z()),
                    new Point(max.x(), max.z()),
                    new Point(min.x(), max.z()))));
        }
        List<Point> points = region.getPoints().stream()
                .map(p -> new Point(p.x(), p.z()))
                .toList();
        return new RegionDimensions("POLYGONAL", min.y(), max.y(), region.getPriority(), distinct(points));
    }

    /**
     * Drops repeated vertices, keeping the first occurrence of each so the outline's winding order
     * survives.
     *
     * <p>Both shapes can produce them. A cuboid only one block wide on an axis has
     * {@code min.x() == max.x()}, so two of its four corners coincide, and a one-by-one region
     * collapses to a single point — walls, paths and markers are all this shape. A polygon carries
     * whatever vertices were clicked, which may repeat or close back onto the first. A consumer
     * drawing the footprint would render a zero-length edge for each repeat, and one computing area
     * from the vertex list would be wrong.</p>
     */
    private static @NotNull List<Point> distinct(@NotNull List<Point> points) {
        return List.copyOf(new LinkedHashSet<>(points));
    }
}
