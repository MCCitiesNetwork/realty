package io.github.md5sha256.realty.command.util;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.WallSign;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Finds safe teleport locations near region signs or within region bounds.
 *
 * <p>All methods must be called on the main server thread (block access required).</p>
 */
public final class SafeLocationFinder {

    private static final Set<Material> UNSAFE_GROUND = Set.of(
            Material.CACTUS,
            Material.MAGMA_BLOCK,
            Material.CAMPFIRE,
            Material.SOUL_CAMPFIRE,
            Material.POINTED_DRIPSTONE,
            Material.SWEET_BERRY_BUSH,
            Material.WITHER_ROSE
    );

    private static final Set<Material> UNSAFE_SURROUNDING = Set.of(
            Material.LAVA,
            Material.FIRE,
            Material.SOUL_FIRE,
            Material.MAGMA_BLOCK
    );

    private SafeLocationFinder() {
    }

    /**
     * Attempts to find a safe teleport location near a region sign.
     *
     * <p>If the sign is a wall sign, the block in front of the sign face is checked first.
     * Then a small radius around the sign is searched. The returned location's yaw/pitch
     * are set so the player faces the sign.</p>
     *
     * @param world        the world containing the sign
     * @param signX        sign block X coordinate
     * @param signY        sign block Y coordinate
     * @param signZ        sign block Z coordinate
     * @param searchRadius radius (in blocks) to search around the sign
     * @return a safe location, or {@code null} if none found
     */
    public static @Nullable Location findSafeNearSign(@NotNull World world,
                                                       int signX, int signY, int signZ,
                                                       int searchRadius) {
        Block signBlock = world.getBlockAt(signX, signY, signZ);

        // Try the block in front of a wall sign first
        if (signBlock.getBlockData() instanceof WallSign wallSign) {
            BlockFace facing = wallSign.getFacing();
            int frontX = signX + facing.getModX();
            int frontZ = signZ + facing.getModZ();
            // Try at sign level and one below (in case the sign is at head height)
            for (int yOffset = 0; yOffset >= -1; yOffset--) {
                int candidateY = signY + yOffset;
                if (isSafe(world, frontX, candidateY, frontZ)) {
                    return buildLocationFacingSign(world, frontX, candidateY, frontZ,
                            signX, signY, signZ);
                }
            }
        }

        // Search in a small cube around the sign
        for (int radius = 1; radius <= searchRadius; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        // Only check the shell of the cube
                        if (Math.abs(dx) != radius && Math.abs(dy) != radius && Math.abs(dz) != radius) {
                            continue;
                        }
                        int cx = signX + dx;
                        int cy = signY + dy;
                        int cz = signZ + dz;
                        if (cy < world.getMinHeight() || cy + 2 > world.getMaxHeight()) {
                            continue;
                        }
                        if (isSafe(world, cx, cy, cz)) {
                            return buildLocationFacingSign(world, cx, cy, cz,
                                    signX, signY, signZ);
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Finds a safe teleport location within a WorldGuard region using an expanding cube search.
     *
     * <p>The search starts at the geometric center of the region and expands outward,
     * checking up to {@code maxTries} candidate positions. Per-face pruning skips
     * directions that contain no solid blocks.</p>
     *
     * @param region   the WorldGuard region to search within
     * @param world    the world containing the region
     * @param maxTries maximum number of positions to check before giving up
     * @return a safe location, or {@code null} if none found
     */
    public static @Nullable Location findSafeInRegion(@NotNull ProtectedRegion region,
                                                       @NotNull World world,
                                                       int maxTries) {
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();

        int startX = (min.x() + max.x()) / 2;
        int startY = (min.y() + max.y()) / 2;
        int startZ = (min.z() + max.z()) / 2;

        // Check the center first
        if (isSafe(world, startX, startY, startZ)) {
            return new Location(world, startX + 0.5, startY, startZ + 0.5);
        }

        int checked = 1;
        // Track which faces still have valid blocks (N, E, S, W, Top, Bottom)
        boolean[] faceActive = {true, true, true, true, true, true};

        for (int radius = 1; checked < maxTries; radius++) {
            boolean anyFaceActive = false;

            // North face (z = -radius)
            if (faceActive[0]) {
                boolean foundValid = false;
                for (int dx = -radius + 1; dx <= radius && checked < maxTries; dx++) {
                    for (int dy = -radius + 1; dy < radius && checked < maxTries; dy++) {
                        int cx = startX + dx;
                        int cy = startY + dy;
                        int cz = startZ - radius;
                        checked++;
                        if (cy < world.getMinHeight() || cy + 2 > world.getMaxHeight()) {
                            continue;
                        }
                        if (!world.getBlockAt(cx, cy, cz).isEmpty()) {
                            foundValid = true;
                        }
                        if (isSafe(world, cx, cy, cz)) {
                            return new Location(world, cx + 0.5, cy, cz + 0.5);
                        }
                    }
                }
                faceActive[0] = foundValid;
                if (foundValid) {
                    anyFaceActive = true;
                }
            }

            // East face (x = +radius)
            if (faceActive[1]) {
                boolean foundValid = false;
                for (int dz = -radius + 1; dz <= radius && checked < maxTries; dz++) {
                    for (int dy = -radius + 1; dy < radius && checked < maxTries; dy++) {
                        int cx = startX + radius;
                        int cy = startY + dy;
                        int cz = startZ + dz;
                        checked++;
                        if (cy < world.getMinHeight() || cy + 2 > world.getMaxHeight()) {
                            continue;
                        }
                        if (!world.getBlockAt(cx, cy, cz).isEmpty()) {
                            foundValid = true;
                        }
                        if (isSafe(world, cx, cy, cz)) {
                            return new Location(world, cx + 0.5, cy, cz + 0.5);
                        }
                    }
                }
                faceActive[1] = foundValid;
                if (foundValid) {
                    anyFaceActive = true;
                }
            }

            // South face (z = +radius)
            if (faceActive[2]) {
                boolean foundValid = false;
                for (int dx = radius - 1; dx >= -radius && checked < maxTries; dx--) {
                    for (int dy = -radius + 1; dy < radius && checked < maxTries; dy++) {
                        int cx = startX + dx;
                        int cy = startY + dy;
                        int cz = startZ + radius;
                        checked++;
                        if (cy < world.getMinHeight() || cy + 2 > world.getMaxHeight()) {
                            continue;
                        }
                        if (!world.getBlockAt(cx, cy, cz).isEmpty()) {
                            foundValid = true;
                        }
                        if (isSafe(world, cx, cy, cz)) {
                            return new Location(world, cx + 0.5, cy, cz + 0.5);
                        }
                    }
                }
                faceActive[2] = foundValid;
                if (foundValid) {
                    anyFaceActive = true;
                }
            }

            // West face (x = -radius)
            if (faceActive[3]) {
                boolean foundValid = false;
                for (int dz = radius - 1; dz >= -radius && checked < maxTries; dz--) {
                    for (int dy = -radius + 1; dy < radius && checked < maxTries; dy++) {
                        int cx = startX - radius;
                        int cy = startY + dy;
                        int cz = startZ + dz;
                        checked++;
                        if (cy < world.getMinHeight() || cy + 2 > world.getMaxHeight()) {
                            continue;
                        }
                        if (!world.getBlockAt(cx, cy, cz).isEmpty()) {
                            foundValid = true;
                        }
                        if (isSafe(world, cx, cy, cz)) {
                            return new Location(world, cx + 0.5, cy, cz + 0.5);
                        }
                    }
                }
                faceActive[3] = foundValid;
                if (foundValid) {
                    anyFaceActive = true;
                }
            }

            // Top face (y = +radius)
            if (faceActive[4]) {
                boolean foundValid = false;
                int cy = startY + radius;
                if (cy >= world.getMinHeight() && cy + 2 <= world.getMaxHeight()) {
                    for (int dx = -radius; dx <= radius && checked < maxTries; dx++) {
                        for (int dz = -radius; dz <= radius && checked < maxTries; dz++) {
                            int cx = startX + dx;
                            int cz = startZ + dz;
                            checked++;
                            if (!world.getBlockAt(cx, cy, cz).isEmpty()) {
                                foundValid = true;
                            }
                            if (isSafe(world, cx, cy, cz)) {
                                return new Location(world, cx + 0.5, cy, cz + 0.5);
                            }
                        }
                    }
                }
                faceActive[4] = foundValid;
                if (foundValid) {
                    anyFaceActive = true;
                }
            }

            // Bottom face (y = -radius)
            if (faceActive[5]) {
                boolean foundValid = false;
                int cy = startY - radius;
                if (cy >= world.getMinHeight() && cy + 2 <= world.getMaxHeight()) {
                    for (int dx = -radius; dx <= radius && checked < maxTries; dx++) {
                        for (int dz = -radius; dz <= radius && checked < maxTries; dz++) {
                            int cx = startX + dx;
                            int cz = startZ + dz;
                            checked++;
                            if (!world.getBlockAt(cx, cy, cz).isEmpty()) {
                                foundValid = true;
                            }
                            if (isSafe(world, cx, cy, cz)) {
                                return new Location(world, cx + 0.5, cy, cz + 0.5);
                            }
                        }
                    }
                }
                faceActive[5] = foundValid;
                if (foundValid) {
                    anyFaceActive = true;
                }
            }

            if (!anyFaceActive) {
                break;
            }
        }
        return null;
    }

    /**
     * Checks whether a player can safely stand at the given feet position.
     */
    private static boolean isSafe(@NotNull World world, int x, int y, int z) {
        if (y - 1 < world.getMinHeight() || y + 2 > world.getMaxHeight()) {
            return false;
        }

        Block ground = world.getBlockAt(x, y - 1, z);
        Block feet = world.getBlockAt(x, y, z);
        Block head = world.getBlockAt(x, y + 1, z);
        Block aboveHead = world.getBlockAt(x, y + 2, z);

        // Ground must be solid, not liquid, not unsafe
        if (!ground.isSolid() || ground.isLiquid() || UNSAFE_GROUND.contains(ground.getType())) {
            return false;
        }

        // Feet and head must be passable and not liquid
        if (!feet.isPassable() || feet.isLiquid()) {
            return false;
        }
        if (!head.isPassable() || head.isLiquid()) {
            return false;
        }

        // Above head must not be liquid
        if (aboveHead.isLiquid()) {
            return false;
        }

        // Check surrounding blocks for dangerous materials
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                for (int dy = -1; dy <= 2; dy++) {
                    Material material = world.getBlockAt(x + dx, y + dy, z + dz).getType();
                    if (UNSAFE_SURROUNDING.contains(material)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static @NotNull Location buildLocationFacingSign(@NotNull World world,
                                                              int x, int y, int z,
                                                              int signX, int signY, int signZ) {
        double dx = (signX + 0.5) - (x + 0.5);
        double dz = (signZ + 0.5) - (z + 0.5);
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        return new Location(world, x + 0.5, y, z + 0.5, yaw, 0f);
    }
}
