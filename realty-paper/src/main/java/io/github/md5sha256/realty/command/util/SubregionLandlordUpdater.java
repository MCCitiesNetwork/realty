package io.github.md5sha256.realty.command.util;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import io.github.md5sha256.realty.database.RealtyLogicImpl;
import io.github.md5sha256.realty.util.ExecutorState;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class SubregionLandlordUpdater {

    private SubregionLandlordUpdater() {}

    /**
     * Finds all WorldGuard child regions of the given parent and updates their
     * lease contract landlord to the new owner. Must be called on the main thread
     * (WorldGuard region lookup). The DB update runs async, then WG owners are
     * updated on the main thread.
     */
    public static void updateChildLandlords(@NotNull String parentRegionId,
                                             @NotNull World world,
                                             @NotNull UUID newLandlord,
                                             @NotNull RealtyLogicImpl logic,
                                             @NotNull ExecutorState executorState) {
        RegionManager regionManager = WorldGuard.getInstance()
                .getPlatform()
                .getRegionContainer()
                .get(BukkitAdapter.adapt(world));
        if (regionManager == null) {
            return;
        }
        ProtectedRegion parent = regionManager.getRegion(parentRegionId);
        if (parent == null) {
            return;
        }
        List<ProtectedRegion> children = new ArrayList<>();
        for (ProtectedRegion region : regionManager.getRegions().values()) {
            if (parent.equals(region.getParent())) {
                children.add(region);
            }
        }
        if (children.isEmpty()) {
            return;
        }
        List<String> childIds = children.stream().map(ProtectedRegion::getId).toList();
        UUID worldId = world.getUID();
        CompletableFuture.runAsync(
                () -> logic.updateSubregionLandlords(childIds, worldId, newLandlord),
                executorState.dbExec()
        );
    }

}
