package io.github.md5sha256.realty.schematic;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import org.jetbrains.annotations.NotNull;

/**
 * The block count of a region, computed from its bounds alone.
 *
 * <p>No blocks are read, so this is cheap enough to gate a capture on before any
 * expensive work starts -- which is the point: an oversized region should be refused
 * before it is copied, not after.</p>
 */
public final class RegionVolume {

    private RegionVolume() {
    }

    /**
     * Block count inclusive of both bounds, in {@code long} arithmetic -- a 2000-block
     * cube overflows an {@code int}.
     */
    public static long of(@NotNull Region region) {
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();
        long width = (long) max.x() - min.x() + 1L;
        long height = (long) max.y() - min.y() + 1L;
        long length = (long) max.z() - min.z() + 1L;
        return width * height * length;
    }

    /**
     * Whether the region is larger than {@code cap}. A cap of zero or less disables
     * the check, matching how the cooldown treats a zero duration.
     */
    public static boolean exceedsCap(@NotNull Region region, long cap) {
        return cap > 0L && of(region) > cap;
    }
}
