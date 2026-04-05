package io.github.md5sha256.realty.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory cache mapping block positions to their realty region info.
 * Used to avoid DB lookups on sign click events and to track registered signs.
 */
public class SignCache {

    private final ConcurrentHashMap<BlockPosition, SignCacheEntry> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<RegionKey, Set<BlockPosition>> regionToSigns = new ConcurrentHashMap<>();

    /**
     * Adds or updates a sign in the cache.
     */
    public void put(@NotNull UUID worldId, int blockX, int blockY, int blockZ,
                    int realtyRegionId, @NotNull String worldGuardRegionId, @NotNull UUID regionWorldId) {
        BlockPosition pos = new BlockPosition(worldId, blockX, blockY, blockZ);
        cache.put(pos, new SignCacheEntry(realtyRegionId, worldGuardRegionId, regionWorldId));
        regionToSigns.computeIfAbsent(new RegionKey(worldGuardRegionId, regionWorldId),
                k -> ConcurrentHashMap.newKeySet()).add(pos);
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
        BlockPosition pos = new BlockPosition(worldId, blockX, blockY, blockZ);
        SignCacheEntry removed = cache.remove(pos);
        if (removed != null) {
            Set<BlockPosition> positions = regionToSigns.get(
                    new RegionKey(removed.worldGuardRegionId(), removed.regionWorldId()));
            if (positions != null) {
                positions.remove(pos);
            }
        }
        return removed;
    }

    /**
     * Evicts all cached signs within a given chunk.
     */
    public void evictChunk(@NotNull UUID worldId, int chunkX, int chunkZ) {
        cache.entrySet().removeIf(entry -> {
            BlockPosition pos = entry.getKey();
            if (pos.worldId().equals(worldId)
                    && Math.floorDiv(pos.x(), 16) == chunkX
                    && Math.floorDiv(pos.z(), 16) == chunkZ) {
                SignCacheEntry value = entry.getValue();
                Set<BlockPosition> positions = regionToSigns.get(
                        new RegionKey(value.worldGuardRegionId(), value.regionWorldId()));
                if (positions != null) {
                    positions.remove(pos);
                }
                return true;
            }
            return false;
        });
    }

    /**
     * Returns an unmodifiable list of cached block positions for the given WorldGuard region.
     *
     * @return the list of sign positions, or an empty list if none are cached
     */
    public @NotNull List<BlockPosition> getSignsByRegion(@NotNull String worldGuardRegionId, @NotNull UUID worldId) {
        Set<BlockPosition> positions = regionToSigns.get(new RegionKey(worldGuardRegionId, worldId));
        if (positions == null || positions.isEmpty()) {
            return Collections.emptyList();
        }
        return List.copyOf(positions);
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

    /**
     * Key for the region-to-signs reverse index.
     */
    public record RegionKey(@NotNull String worldGuardRegionId, @NotNull UUID worldId) {}
}
