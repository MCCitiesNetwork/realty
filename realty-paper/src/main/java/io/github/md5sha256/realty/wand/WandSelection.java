package io.github.md5sha256.realty.wand;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Polygonal2DRegion;
import com.sk89q.worldedit.regions.Region;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A player's in-progress wand selection: an ordered footprint of marked points plus a vertical span
 * (floor/ceiling) set separately via the height dialog.
 *
 * <p>The footprint shape is inferred from the point count — exactly two points define a rectangle
 * (opposite corners), three or more define an arbitrary polygon. The height is <em>not</em> taken
 * from where the player clicked; players mark the footprint on the ground and set the vertical span
 * afterwards. Mutation is package-private — only {@link SubregionWandManager} edits a selection.</p>
 */
public final class WandSelection {

    private final World world;
    private final List<BlockVector3> points = new ArrayList<>();
    private Integer floorY;
    private Integer ceilingY;

    WandSelection(@NotNull World world) {
        this.world = world;
    }

    void addPoint(@NotNull BlockVector3 point) {
        points.add(point);
    }

    /** Removes the most recently marked point; returns {@code false} if there was none. */
    boolean removeLastPoint() {
        if (points.isEmpty()) {
            return false;
        }
        points.remove(points.size() - 1);
        return true;
    }

    public @NotNull World world() {
        return world;
    }

    public @NotNull List<BlockVector3> points() {
        return List.copyOf(points);
    }

    public int size() {
        return points.size();
    }

    /** Two points make a rectangle; three or more make a polygon. */
    public boolean isComplete() {
        return points.size() >= 2;
    }

    public boolean isPolygon() {
        return points.size() >= 3;
    }

    public boolean heightSet() {
        return floorY != null && ceilingY != null;
    }

    /** Footprint and height both set — ready to build a region. */
    public boolean isReady() {
        return isComplete() && heightSet();
    }

    public @Nullable Integer floorY() {
        return floorY;
    }

    public @Nullable Integer ceilingY() {
        return ceilingY;
    }

    public void setHeight(int floor, int ceiling) {
        this.floorY = Math.min(floor, ceiling);
        this.ceilingY = Math.max(floor, ceiling);
    }

    public int minPointY() {
        int min = Integer.MAX_VALUE;
        for (BlockVector3 point : points) {
            min = Math.min(min, point.y());
        }
        return min == Integer.MAX_VALUE ? 0 : min;
    }

    public int maxPointY() {
        int max = Integer.MIN_VALUE;
        for (BlockVector3 point : points) {
            max = Math.max(max, point.y());
        }
        return max == Integer.MIN_VALUE ? 0 : max;
    }

    public @NotNull Region toRegion() {
        if (!isReady()) {
            throw new IllegalStateException("Selection is incomplete");
        }
        if (points.size() == 2) {
            BlockVector3 a = points.get(0);
            BlockVector3 b = points.get(1);
            BlockVector3 min = BlockVector3.at(Math.min(a.x(), b.x()), floorY, Math.min(a.z(), b.z()));
            BlockVector3 max = BlockVector3.at(Math.max(a.x(), b.x()), ceilingY, Math.max(a.z(), b.z()));
            return new CuboidRegion(min, max);
        }
        List<BlockVector2> footprint = new ArrayList<>(points.size());
        for (BlockVector3 point : points) {
            footprint.add(BlockVector2.at(point.x(), point.z()));
        }
        return new Polygonal2DRegion(BukkitAdapter.adapt(world), footprint, floorY, ceilingY);
    }
}
