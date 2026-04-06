package io.github.md5sha256.realty.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Assertions;

class SignCacheTest {

    private SignCache cache;

    private static final UUID WORLD_A = UUID.randomUUID();
    private static final UUID WORLD_B = UUID.randomUUID();
    private static final String REGION_A = "region_a";
    private static final String REGION_B = "region_b";

    @BeforeEach
    void setUp() {
        cache = new SignCache();
    }

    @Nested
    @DisplayName("put and get")
    class PutAndGet {

        @Test
        @DisplayName("round-trip put then get returns correct entry")
        void putAndGet() {
            cache.put(WORLD_A, 10, 64, 20, 1, REGION_A, WORLD_A);

            SignCache.SignCacheEntry entry = cache.get(WORLD_A, 10, 64, 20);
            Assertions.assertNotNull(entry);
            Assertions.assertEquals(1, entry.realtyRegionId());
            Assertions.assertEquals(REGION_A, entry.worldGuardRegionId());
            Assertions.assertEquals(WORLD_A, entry.regionWorldId());
        }

        @Test
        @DisplayName("get returns null for unknown position")
        void getReturnsNullForUnknown() {
            Assertions.assertNull(cache.get(WORLD_A, 99, 99, 99));
        }

        @Test
        @DisplayName("put overwrites existing entry at same position")
        void putOverwrites() {
            cache.put(WORLD_A, 10, 64, 20, 1, REGION_A, WORLD_A);
            cache.put(WORLD_A, 10, 64, 20, 2, REGION_B, WORLD_A);

            SignCache.SignCacheEntry entry = cache.get(WORLD_A, 10, 64, 20);
            Assertions.assertNotNull(entry);
            Assertions.assertEquals(2, entry.realtyRegionId());
            Assertions.assertEquals(REGION_B, entry.worldGuardRegionId());
        }
    }

    @Nested
    @DisplayName("remove")
    class Remove {

        @Test
        @DisplayName("returns entry and removes from cache")
        void removeReturnsAndCleans() {
            cache.put(WORLD_A, 10, 64, 20, 1, REGION_A, WORLD_A);

            SignCache.SignCacheEntry removed = cache.remove(WORLD_A, 10, 64, 20);
            Assertions.assertNotNull(removed);
            Assertions.assertEquals(1, removed.realtyRegionId());
            Assertions.assertNull(cache.get(WORLD_A, 10, 64, 20));
        }

        @Test
        @DisplayName("returns null for unknown position")
        void removeReturnsNullForUnknown() {
            Assertions.assertNull(cache.remove(WORLD_A, 99, 99, 99));
        }

        @Test
        @DisplayName("cleans up reverse index")
        void removeCleansReverseIndex() {
            cache.put(WORLD_A, 10, 64, 20, 1, REGION_A, WORLD_A);
            cache.remove(WORLD_A, 10, 64, 20);

            List<SignCache.BlockPosition> signs = cache.getSignsByRegion(REGION_A, WORLD_A);
            Assertions.assertTrue(signs.isEmpty());
        }
    }

    @Nested
    @DisplayName("evictChunk")
    class EvictChunk {

        @Test
        @DisplayName("removes all signs in the target chunk")
        void removesSignsInChunk() {
            // Block coords 0-15 are chunk 0
            cache.put(WORLD_A, 0, 64, 0, 1, REGION_A, WORLD_A);
            cache.put(WORLD_A, 15, 70, 15, 2, REGION_A, WORLD_A);

            cache.evictChunk(WORLD_A, 0, 0);

            Assertions.assertNull(cache.get(WORLD_A, 0, 64, 0));
            Assertions.assertNull(cache.get(WORLD_A, 15, 70, 15));
        }

        @Test
        @DisplayName("preserves signs in other chunks")
        void preservesOtherChunks() {
            cache.put(WORLD_A, 0, 64, 0, 1, REGION_A, WORLD_A);    // chunk (0, 0)
            cache.put(WORLD_A, 16, 64, 16, 2, REGION_A, WORLD_A);  // chunk (1, 1)

            cache.evictChunk(WORLD_A, 0, 0);

            Assertions.assertNull(cache.get(WORLD_A, 0, 64, 0));
            Assertions.assertNotNull(cache.get(WORLD_A, 16, 64, 16));
        }

        @Test
        @DisplayName("handles negative coordinates with Math.floorDiv")
        void negativeCoordinates() {
            // Block x=-1 should be in chunk -1, not chunk 0
            cache.put(WORLD_A, -1, 64, -1, 1, REGION_A, WORLD_A);
            // Block x=0 should be in chunk 0
            cache.put(WORLD_A, 0, 64, 0, 2, REGION_A, WORLD_A);

            // Evict chunk (-1, -1) — should only remove the sign at (-1, 64, -1)
            cache.evictChunk(WORLD_A, -1, -1);

            Assertions.assertNull(cache.get(WORLD_A, -1, 64, -1));
            Assertions.assertNotNull(cache.get(WORLD_A, 0, 64, 0));
        }

        @Test
        @DisplayName("cleans up reverse index for evicted signs")
        void cleansReverseIndex() {
            cache.put(WORLD_A, 0, 64, 0, 1, REGION_A, WORLD_A);
            cache.put(WORLD_A, 16, 64, 16, 2, REGION_A, WORLD_A); // different chunk

            cache.evictChunk(WORLD_A, 0, 0);

            List<SignCache.BlockPosition> signs = cache.getSignsByRegion(REGION_A, WORLD_A);
            Assertions.assertEquals(1, signs.size());
            Assertions.assertEquals(new SignCache.BlockPosition(WORLD_A, 16, 64, 16), signs.get(0));
        }
    }

    @Nested
    @DisplayName("getSignsByRegion")
    class GetSignsByRegion {

        @Test
        @DisplayName("returns empty list for unknown region")
        void emptyForUnknown() {
            List<SignCache.BlockPosition> signs = cache.getSignsByRegion("nonexistent", WORLD_A);
            Assertions.assertNotNull(signs);
            Assertions.assertTrue(signs.isEmpty());
        }

        @Test
        @DisplayName("returns all signs for a region")
        void returnsAllForRegion() {
            cache.put(WORLD_A, 10, 64, 20, 1, REGION_A, WORLD_A);
            cache.put(WORLD_A, 30, 64, 40, 2, REGION_A, WORLD_A);

            List<SignCache.BlockPosition> signs = cache.getSignsByRegion(REGION_A, WORLD_A);
            Assertions.assertEquals(2, signs.size());
        }

        @Test
        @DisplayName("does not mix regions")
        void doesNotMixRegions() {
            cache.put(WORLD_A, 10, 64, 20, 1, REGION_A, WORLD_A);
            cache.put(WORLD_A, 30, 64, 40, 2, REGION_B, WORLD_A);

            List<SignCache.BlockPosition> signsA = cache.getSignsByRegion(REGION_A, WORLD_A);
            List<SignCache.BlockPosition> signsB = cache.getSignsByRegion(REGION_B, WORLD_A);

            Assertions.assertEquals(1, signsA.size());
            Assertions.assertEquals(1, signsB.size());
            Assertions.assertEquals(new SignCache.BlockPosition(WORLD_A, 10, 64, 20), signsA.get(0));
            Assertions.assertEquals(new SignCache.BlockPosition(WORLD_A, 30, 64, 40), signsB.get(0));
        }

        @Test
        @DisplayName("distinguishes same region ID by world")
        void distinguishesByWorld() {
            cache.put(WORLD_A, 10, 64, 20, 1, REGION_A, WORLD_A);
            cache.put(WORLD_B, 10, 64, 20, 2, REGION_A, WORLD_B);

            List<SignCache.BlockPosition> signsWorldA = cache.getSignsByRegion(REGION_A, WORLD_A);
            List<SignCache.BlockPosition> signsWorldB = cache.getSignsByRegion(REGION_A, WORLD_B);

            Assertions.assertEquals(1, signsWorldA.size());
            Assertions.assertEquals(1, signsWorldB.size());
            Assertions.assertEquals(WORLD_A, signsWorldA.get(0).worldId());
            Assertions.assertEquals(WORLD_B, signsWorldB.get(0).worldId());
        }
    }
}
