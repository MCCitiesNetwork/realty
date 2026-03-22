package io.github.md5sha256.realty.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory cache mapping block positions to their realty region info.
 * Used to avoid DB lookups on sign click events and to track registered signs.
 */
public class SignCache {

    private final ConcurrentHashMap<BlockPosition, SignCacheEntry> cache = new ConcurrentHashMap<>();

    /**
     * Adds or updates a sign in the cache.
     */
    public void put(@NotNull UUID worldId, int blockX, int blockY, int blockZ,
                    int realtyRegionId, @NotNull String worldGuardRegionId, @NotNull UUID regionWorldId) {
        cache.put(new BlockPosition(worldId, blockX, blockY, blockZ),
                new SignCacheEntry(realtyRegionId, worldGuardRegionId, regionWorldId));
    }

    /**
     * Looks up a cached sign entry by block position.
     *
     * @return the cache entry, or null if the position is not a registered sign
     */
    public @Nullable SignCacheEntry get(@NotNull UUID worldId, int blockX, int blockY, int blockZ) {
        return cache.get(new BlockPosition(worldId, blockX, blockY, blockZ));
    }

    /**
     * Removes a sign from the cache.
     *
     * @return the removed entry, or null if it was not cached
     */
    public @Nullable SignCacheEntry remove(@NotNull UUID worldId, int blockX, int blockY, int blockZ) {
        return cache.remove(new BlockPosition(worldId, blockX, blockY, blockZ));
    }

    /**
     * Evicts all cached signs within a given chunk.
     */
    public void evictChunk(@NotNull UUID worldId, int chunkX, int chunkZ) {
        cache.entrySet().removeIf(entry -> {
            BlockPosition pos = entry.getKey();
            return pos.worldId().equals(worldId)
                    && Math.floorDiv(pos.x(), 16) == chunkX
                    && Math.floorDiv(pos.z(), 16) == chunkZ;
        });
    }

    /**
     * Block position key for the sign cache.
     */
    public record BlockPosition(@NotNull UUID worldId, int x, int y, int z) {}

    /**
     * Cached sign entry linking a block position to its realty region.
     */
    public record SignCacheEntry(int realtyRegionId,
                                  @NotNull String worldGuardRegionId,
                                  @NotNull UUID regionWorldId) {}
}
