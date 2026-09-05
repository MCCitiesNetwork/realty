package io.github.md5sha256.realty.schematic;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The volume a capture covers: the region's footprint, from a floor up to its ceiling.
 *
 * <p>A WorldGuard region often runs from bedrock to the build limit, because that is the
 * cheapest way to claim a plot. The building a visitor wants to see starts where the
 * ground is, and a capture of the whole column records a few hundred blocks of stone
 * beneath it -- which is what the preview then shows. The floor is therefore the block
 * the capturing player stands on: standing on the doorstep captures the house.</p>
 */
public final class CaptureBounds {

    private CaptureBounds() {
    }

    /**
     * The region between {@code min} and {@code max}, with its floor raised to
     * {@code floorY} where that lies inside the region.
     *
     * <p>A floor below the region changes nothing. A floor above the region also changes
     * nothing: there is no sensible slice to take from above, and refusing would turn a
     * capture run from a balcony over the plot into an error, so the whole region is
     * taken instead.</p>
     */
    public static @NotNull CuboidRegion fromFloor(@Nullable World world,
                                                  @NotNull BlockVector3 min,
                                                  @NotNull BlockVector3 max,
                                                  int floorY) {
        if (floorY > min.y() && floorY <= max.y()) {
            return new CuboidRegion(world, min.withY(floorY), max);
        }
        return new CuboidRegion(world, min, max);
    }
}
