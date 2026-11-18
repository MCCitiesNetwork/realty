package io.github.md5sha256.realty.api;

import com.sk89q.worldguard.protection.flags.registry.SimpleFlagRegistry;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.managers.index.ChunkHashTable;
import com.sk89q.worldguard.protection.managers.index.PriorityRTreeIndex;
import com.sk89q.worldguard.protection.managers.storage.MemoryRegionDatabase;
import com.sk89q.worldguard.protection.managers.storage.RegionDatabase;
import org.jetbrains.annotations.NotNull;

/** Real WorldGuard region managers for tests, so that region trees behave as they do on a server. */
final class TestRegionManagers {

    private TestRegionManagers() {
    }

    /** A manager that keeps its regions in memory and always saves. */
    static @NotNull RegionManager inMemory() {
        return storedIn(new MemoryRegionDatabase());
    }

    static @NotNull RegionManager storedIn(@NotNull RegionDatabase store) {
        return new RegionManager(store,
                new ChunkHashTable.Factory(new PriorityRTreeIndex.Factory()), new SimpleFlagRegistry());
    }
}
