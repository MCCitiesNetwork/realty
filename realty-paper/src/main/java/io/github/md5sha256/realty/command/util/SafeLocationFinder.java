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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

/**
 * Finds safe teleport locations near region signs or within region bounds.
 *
 * <p>Safety is determined by a configurable {@link Predicate} that tests the
 * feet-level block. The predicate may inspect surrounding blocks via
 * {@link Block#getRelative(BlockFace)}.</p>
 *
 * <p>Chunks are loaded asynchronously via {@link World#getChunkAtAsync(int, int)}.
 * Since {@code getChunkAtAsync} always completes on the main thread, all block
 * access in safety-check callbacks is guaranteed to run on the main thread.</p>
 */
public final class SafeLocationFinder {

    private static final int BATCH_SIZE = 512;

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

    private final Predicate<Block> safetyPredicate;

    /**
     * Creates a finder with a custom safety predicate.
     *
     * @param safetyPredicate predicate that tests the feet-level block;
     *                        returns {@code true} if safe to teleport to
     */
    public SafeLocationFinder(@NotNull Predicate<Block> safetyPredicate) {
        this.safetyPredicate = safetyPredicate;
    }

    /**
     * Creates a finder with the built-in safety predicate.
     */
    public SafeLocationFinder() {
        this(SafeLocationFinder::defaultIsSafe);
    }

    /**
     * Returns the default safety predicate used when no custom predicate is supplied.
     *
     * @return a predicate that checks ground solidity, passability, liquids, and
     * nearby dangerous blocks
     */
    public static @NotNull Predicate<Block> defaultPredicate() {
        return SafeLocationFinder::defaultIsSafe;
    }

    /**
     * Default safety check: solid non-hazardous ground, passable feet/head space,
     * no liquids or dangerous surrounding blocks.
     */
    private static boolean defaultIsSafe(@NotNull Block feetBlock) {
        World world = feetBlock.getWorld();
        int x = feetBlock.getX();
        int y = feetBlock.getY();
        int z = feetBlock.getZ();

        if (y - 1 < world.getMinHeight() || y + 2 > world.getMaxHeight()) {
            return false;
        }

        Block ground = world.getBlockAt(x, y - 1, z);
        Block head = world.getBlockAt(x, y + 1, z);
        Block aboveHead = world.getBlockAt(x, y + 2, z);

        // Ground must be solid, not liquid, not unsafe
        if (!ground.isSolid() || ground.isLiquid() || UNSAFE_GROUND.contains(ground.getType())) {
            return false;
        }

        // Feet and head must be passable and not liquid
        if (!feetBlock.isPassable() || feetBlock.isLiquid()) {
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

    /**
     * Asynchronously finds a safe teleport location near a region sign.
     *
     * <p>Loads all chunks in the search area asynchronously, then performs
     * the safety search on the main thread.</p>
     *
     * @param world        the world containing the sign
     * @param signX        sign block X coordinate
     * @param signY        sign block Y coordinate
     * @param signZ        sign block Z coordinate
     * @param searchRadius radius (in blocks) to search around the sign
     * @return a future completing with a safe location, or {@code null} if none found
     */
    public @NotNull CompletableFuture<@Nullable Location> findSafeNearSign(
            @NotNull World world,
            int signX, int signY, int signZ,
            int searchRadius) {

        int minCX = Math.floorDiv(signX - searchRadius, 16);
        int maxCX = Math.floorDiv(signX + searchRadius, 16);
        int minCZ = Math.floorDiv(signZ - searchRadius, 16);
        int maxCZ = Math.floorDiv(signZ + searchRadius, 16);

        List<CompletableFuture<?>> chunkFutures = new ArrayList<>();
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                chunkFutures.add(world.getChunkAtAsync(cx, cz));
            }
        }

        return CompletableFuture.allOf(chunkFutures.toArray(CompletableFuture[]::new))
                .thenApply(v -> {
                    Block signBlock = world.getBlockAt(signX, signY, signZ);

                    // Try the block in front of a wall sign first
                    if (signBlock.getBlockData() instanceof WallSign wallSign) {
                        BlockFace facing = wallSign.getFacing();
                        int frontX = signX + facing.getModX();
                        int frontZ = signZ + facing.getModZ();
                        for (int yOffset = 0; yOffset >= -1; yOffset--) {
                            int candidateY = signY + yOffset;
                            if (isSafe(world, frontX, candidateY, frontZ)) {
                                return buildLocationFacingSign(world, frontX, candidateY, frontZ,
                                        signX, signY, signZ);
                            }
                        }
                    }

                    // Search expanding cube shells around the sign
                    CubeShellBlockIterator iterator = new CubeShellBlockIterator(
                            signX, signY, signZ, searchRadius);
                    while (iterator.hasNext()) {
                        BlockPosition pos = iterator.next();
                        if (pos.y() < world.getMinHeight() || pos.y() + 2 > world.getMaxHeight()) {
                            continue;
                        }
                        if (isSafe(world, pos.x(), pos.y(), pos.z())) {
                            return buildLocationFacingSign(world, pos.x(), pos.y(), pos.z(),
                                    signX, signY, signZ);
                        }
                    }
                    return null;
                });
    }

    /**
     * Asynchronously finds a safe teleport location within a WorldGuard region.
     *
     * <p>Starts at the geometric center and expands outward, loading chunks in
     * batches asynchronously. Safety checks run on the main thread after each
     * batch of chunks is loaded.</p>
     *
     * @param region   the WorldGuard region to search within
     * @param world    the world containing the region
     * @param maxTries maximum number of positions to check before giving up
     * @return a future completing with a safe location, or {@code null} if none found
     */
    public @NotNull CompletableFuture<@Nullable Location> findSafeInRegion(
            @NotNull ProtectedRegion region,
            @NotNull World world,
            int maxTries) {

        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();

        int startX = (min.x() + max.x()) / 2;
        int startY = (min.y() + max.y()) / 2;
        int startZ = (min.z() + max.z()) / 2;

        // Load center chunk and check center first
        return world.getChunkAtAsync(Math.floorDiv(startX, 16), Math.floorDiv(startZ, 16))
                .thenCompose(chunk -> {
                    if (isSafe(world, startX, startY, startZ)) {
                        return CompletableFuture.completedFuture(
                                new Location(world, startX + 0.5, startY, startZ + 0.5));
                    }

                    // Face pruning is disabled for async (predicate always returns true)
                    // so that block access is not required during iteration
                    ExpandingCubeBlockIterator iterator = new ExpandingCubeBlockIterator(
                            startX, startY, startZ,
                            maxTries - 1,
                            world.getMinHeight(), world.getMaxHeight(),
                            pos -> true);

                    return processNextBatch(iterator, world);
                });
    }

    /**
     * Collects the next batch of positions from the iterator, loads required
     * chunks asynchronously, then checks safety on the main thread.
     */
    private @NotNull CompletableFuture<@Nullable Location> processNextBatch(
            @NotNull Iterator<BlockPosition> iterator,
            @NotNull World world) {

        List<BlockPosition> batch = new ArrayList<>(BATCH_SIZE);
        List<CompletableFuture<?>> chunkFutures = new ArrayList<>();
        Set<Long> seenChunks = new LinkedHashSet<>();

        while (iterator.hasNext() && batch.size() < BATCH_SIZE) {
            BlockPosition pos = iterator.next();
            batch.add(pos);
            int cx = Math.floorDiv(pos.x(), 16);
            int cz = Math.floorDiv(pos.z(), 16);
            long key = chunkKey(cx, cz);
            if (seenChunks.add(key)) {
                chunkFutures.add(world.getChunkAtAsync(cx, cz));
            }
        }

        if (batch.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.allOf(chunkFutures.toArray(CompletableFuture[]::new))
                .thenCompose(v -> {
                    for (BlockPosition pos : batch) {
                        if (isSafe(world, pos.x(), pos.y(), pos.z())) {
                            return CompletableFuture.completedFuture(
                                    new Location(world, pos.x() + 0.5, pos.y(), pos.z() + 0.5));
                        }
                    }
                    return processNextBatch(iterator, world);
                });
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    /**
     * Delegates to the configured safety predicate for the given feet-level position.
     */
    public boolean isSafe(@NotNull World world, int x, int y, int z) {
        Block feetBlock = world.getBlockAt(x, y, z);
        return safetyPredicate.test(feetBlock);
    }
}
