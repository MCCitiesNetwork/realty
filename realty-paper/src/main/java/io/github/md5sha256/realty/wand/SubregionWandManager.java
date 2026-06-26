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
import org.jetbrains.annotations.UnmodifiableView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Holds each player's in-progress wand selection: an ordered footprint of marked points plus a
 * vertical span (floor/ceiling) set separately via the height dialog.
 *
 * <p>The footprint shape is inferred from the point count — exactly two points define a rectangle
 * (opposite corners), three or more define an arbitrary polygon. The height is <em>not</em> taken
 * from where the player clicked; players mark the footprint on the ground and set the vertical
 * span afterwards, so there's never a need to click a block floating in the air.</p>
 *
 * <p>A plain {@link HashMap} is intentional: every access happens on the server main thread —
 * point capture in the interact event, the particle render task, and dialog open. The dialog's
 * single async stage captures the {@link Region} before leaving the main thread, so this map is
 * never touched concurrently.</p>
 */
public final class SubregionWandManager {

    /**
     * A selection in progress: the world, the marked footprint points, and the (optional) height.
     */
    public static final class WandSelection {
        private final World world;
        private final List<BlockVector3> points = new ArrayList<>();
        private Integer floorY;
        private Integer ceilingY;

        private WandSelection(@NotNull World world) {
            this.world = world;
        }

        public @NotNull World world() {
            return world;
        }

        public @NotNull @UnmodifiableView List<BlockVector3> points() {
            return Collections.unmodifiableList(points);
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

    private final Map<UUID, WandSelection> selections = new HashMap<>();

    /**
     * Appends a marked footprint point for the player. If the player's stored selection is in a
     * different world (they moved worlds), it is reset to the new world first.
     */
    public @NotNull WandSelection addPoint(@NotNull UUID playerId, @NotNull World world,
                                           @NotNull BlockVector3 point) {
        WandSelection selection = selections.get(playerId);
        if (selection == null || selection.world != world) {
            selection = new WandSelection(world);
            selections.put(playerId, selection);
        }
        selection.points.add(point);
        return selection;
    }

    /**
     * Removes the player's most recently marked point. Returns the selection (possibly now empty),
     * or {@code null} if there was nothing to remove.
     */
    public @Nullable WandSelection removeLastPoint(@NotNull UUID playerId) {
        WandSelection selection = selections.get(playerId);
        if (selection == null || selection.points.isEmpty()) {
            return null;
        }
        selection.points.remove(selection.points.size() - 1);
        return selection;
    }

    public @Nullable WandSelection get(@NotNull UUID playerId) {
        return selections.get(playerId);
    }

    public boolean isComplete(@NotNull UUID playerId) {
        WandSelection selection = selections.get(playerId);
        return selection != null && selection.isComplete();
    }

    public void clear(@NotNull UUID playerId) {
        selections.remove(playerId);
    }
}
