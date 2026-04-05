package io.github.md5sha256.realty.api;

import io.github.md5sha256.realty.database.Database;
import io.github.md5sha256.realty.database.SqlSessionWrapper;
import io.github.md5sha256.realty.database.entity.RealtyRegionEntity;
import io.github.md5sha256.realty.database.entity.RealtySignEntity;
import net.kyori.adventure.text.Component;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Utility for resolving sign profiles and applying sign text to blocks.
 * Centralises the resolve-and-apply logic used by {@link ProfileApplicator},
 * {@link io.github.md5sha256.realty.command.SignCommand}, and
 * {@link io.github.md5sha256.realty.listener.SignInteractionListener}.
 */
public class SignTextApplicator {

    private final RegionProfileService regionProfileService;
    private final RealtyBackend logic;
    private final Database database;
    private final SignCache signCache;
    private final Logger logger;

    public SignTextApplicator(@NotNull RegionProfileService regionProfileService,
                              @NotNull RealtyBackend logic,
                              @NotNull Database database,
                              @NotNull SignCache signCache,
                              @NotNull Logger logger) {
        this.regionProfileService = regionProfileService;
        this.logic = logic;
        this.database = database;
        this.signCache = signCache;
        this.logger = logger;
    }

    /**
     * Applies the resolved sign profile text to a sign block.
     *
     * @param sign    the sign block state
     * @param profile the resolved sign profile with placeholder-substituted lines
     */
    public static void applyLines(@NotNull Sign sign, @NotNull RegionProfileService.ResolvedSignProfile profile) {
        List<Component> lines = profile.lines();
        for (int i = 0; i < 4; i++) {
            sign.getSide(Side.FRONT).line(i, i < lines.size() ? lines.get(i) : Component.empty());
        }
        sign.update();
    }

    /**
     * Clears all text from a sign's front side.
     */
    public static void clearLines(@NotNull Sign sign) {
        for (int i = 0; i < 4; i++) {
            sign.getSide(Side.FRONT).line(i, Component.empty());
        }
        sign.update();
    }

    /**
     * Resolves the sign profile for a region, then applies it to the sign block.
     * Returns false if the block at the sign entity's position is no longer a sign.
     * Must be called on the main thread.
     *
     * @param world      the world containing the sign
     * @param signEntity the sign entity from the database
     * @param regionId   the WorldGuard region ID
     * @param state      the region's current state
     * @param placeholders the resolved placeholders for the region
     * @return true if the sign text was applied, false if the block is no longer a sign
     */
    public boolean applySignText(@NotNull World world,
                                  int blockX, int blockY, int blockZ,
                                  @NotNull String regionId,
                                  @NotNull RegionState state,
                                  @NotNull java.util.Map<String, String> placeholders) {
        Block block = world.getBlockAt(blockX, blockY, blockZ);
        if (!(block.getState(false) instanceof Sign sign)) {
            return false;
        }
        RegionProfileService.ResolvedSignProfile profile =
                regionProfileService.resolveSignProfile(regionId, state, placeholders);
        if (profile != null) {
            applyLines(sign, profile);
        }
        return true;
    }

    public enum ApplyResult {
        BLOCK_NOT_LOADED,
        SUCCESS,
        FAILED
    }

    @NotNull
    public ApplyResult applySignTextIfLoaded(@NotNull World world,
                                 int blockX, int blockY, int blockZ,
                                 @NotNull String regionId,
                                 @NotNull RegionState state,
                                 @NotNull java.util.Map<String, String> placeholders) {
        Block block = world.getBlockAt(blockX, blockY, blockZ);
        if (!block.getChunk().isLoaded()) {
            return ApplyResult.BLOCK_NOT_LOADED;
        }
        if (!(block.getState(false) instanceof Sign sign)) {
            return ApplyResult.FAILED;
        }
        RegionProfileService.ResolvedSignProfile profile =
                regionProfileService.resolveSignProfile(regionId, state, placeholders);
        if (profile != null) {
            applyLines(sign, profile);
        }
        return ApplyResult.SUCCESS;
    }

    /**
     * Updates all loaded (cached) signs for a given WorldGuard region.
     * Must be called on the main thread.
     *
     * @param world        the world containing the signs
     * @param regionId     the WorldGuard region ID
     * @param state        the region's current state
     * @param placeholders the resolved placeholders for the region
     */
    public void updateLoadedSigns(@NotNull World world,
                                   @NotNull String regionId,
                                   @NotNull RegionState state,
                                   @NotNull java.util.Map<String, String> placeholders) {
        List<SignCache.BlockPosition> positions = signCache.getSignsByRegion(regionId, world.getUID());
        for (SignCache.BlockPosition pos : positions) {
            applySignText(world, pos.x(), pos.y(), pos.z(), regionId, state, placeholders);
        }
    }

    /**
     * Fetches sign entities for a chunk from the database, populates the cache,
     * and applies sign text on the main thread. Stale signs (blocks that are no
     * longer signs) are cleaned up from the database.
     * Must be called off the main thread.
     *
     * @param world     the world
     * @param chunkX    chunk X coordinate
     * @param chunkZ    chunk Z coordinate
     * @param mainThreadExec executor for scheduling main-thread work
     */
    public void loadAndApplyChunkSigns(@NotNull World world,
                                        int chunkX,
                                        int chunkZ,
                                        @NotNull java.util.concurrent.Executor mainThreadExec) {
        java.util.UUID worldId = world.getUID();
        try (SqlSessionWrapper session = database.openSession(true)) {
            List<RealtySignEntity> signs = session.realtySignMapper()
                    .selectByChunk(worldId, chunkX, chunkZ);
            if (signs.isEmpty()) {
                return;
            }
            List<SignWithRegion> resolved = new ArrayList<>(signs.size());
            for (RealtySignEntity signEntity : signs) {
                RealtyRegionEntity region = session.realtyRegionMapper()
                        .selectById(signEntity.realtyRegionId());
                if (region == null) {
                    continue;
                }
                signCache.put(signEntity.worldId(), signEntity.blockX(), signEntity.blockY(),
                        signEntity.blockZ(), signEntity.realtyRegionId(),
                        region.worldGuardRegionId(), region.worldId());
                RealtyBackend.RegionWithState rws = logic.getRegionWithState(
                        region.worldGuardRegionId(), region.worldId());
                if (rws != null) {
                    resolved.add(new SignWithRegion(signEntity, region.worldGuardRegionId(),
                            rws.state(), rws.placeholders()));
                }
            }
            if (!resolved.isEmpty()) {
                mainThreadExec.execute(() -> {
                    List<RealtySignEntity> stale = new ArrayList<>();
                    for (SignWithRegion swr : resolved) {
                        if (applySignTextIfLoaded(world, swr.signEntity().blockX(),
                                swr.signEntity().blockY(), swr.signEntity().blockZ(),
                                swr.regionId(), swr.state(), swr.placeholders()) == ApplyResult.BLOCK_NOT_LOADED) {
                            stale.add(swr.signEntity());
                        }
                    }
                    if (!stale.isEmpty()) {
                        cleanupStaleSigns(stale);
                    }
                });
            }
        }
    }

    /**
     * Deletes stale sign entries from the database.
     */
    public void cleanupStaleSigns(@NotNull List<RealtySignEntity> stale) {
        try (SqlSessionWrapper session = database.openSession(true)) {
            for (RealtySignEntity signEntity : stale) {
                session.realtySignMapper().deleteByPosition(
                        signEntity.worldId(), signEntity.blockX(),
                        signEntity.blockY(), signEntity.blockZ());
            }
        }
        logger.info("Cleaned up " + stale.size() + " stale sign(s)");
    }

    private record SignWithRegion(@NotNull RealtySignEntity signEntity,
                                   @NotNull String regionId,
                                   @NotNull RegionState state,
                                   @NotNull java.util.Map<String, String> placeholders) {}
}
