package io.github.md5sha256.realty.listener;

import io.github.md5sha256.realty.api.RegionProfileService;
import io.github.md5sha256.realty.api.SignCache;
import io.github.md5sha256.realty.api.SignTextApplicator;
import io.github.md5sha256.realty.database.Database;
import io.github.md5sha256.realty.api.RealtyApi;
import io.github.md5sha256.realty.database.SqlSessionWrapper;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.localisation.MessageKeys;
import io.github.md5sha256.realty.util.ExecutorState;
import io.papermc.paper.event.player.PlayerOpenSignEvent;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSignOpenEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Handles sign click interactions, sign break cleanup, and chunk-based sign caching.
 */
public class SignInteractionListener implements Listener {

    private final Database database;
    private final RealtyApi logic;
    private final RegionProfileService regionProfileService;
    private final ExecutorState executorState;
    private final SignCache signCache;
    private final SignTextApplicator signTextApplicator;
    private final MessageContainer messages;

    public SignInteractionListener(@NotNull Database database,
                                    @NotNull RealtyApi logic,
                                    @NotNull RegionProfileService regionProfileService,
                                    @NotNull ExecutorState executorState,
                                    @NotNull SignCache signCache,
                                    @NotNull SignTextApplicator signTextApplicator,
                                    @NotNull MessageContainer messages) {
        this.database = database;
        this.logic = logic;
        this.regionProfileService = regionProfileService;
        this.executorState = executorState;
        this.signCache = signCache;
        this.signTextApplicator = signTextApplicator;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteract(@NotNull PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null || !(block.getState(false) instanceof Sign)) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        SignCache.SignCacheEntry entry = signCache.get(
                block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        if (entry == null) {
            return;
        }
        Player player = event.getPlayer();
        executorState.dbExec().execute(() -> {
            RealtyApi.RegionWithState rws = logic.getRegionWithState(
                    entry.worldGuardRegionId(), entry.regionWorldId());
            if (rws == null) {
                return;
            }
            RegionProfileService.ResolvedSignProfile profile =
                    regionProfileService.resolveSignProfile(
                            entry.worldGuardRegionId(), rws.state(), rws.placeholders());
            if (profile == null) {
                return;
            }
            List<String> commands;
            if (action == Action.RIGHT_CLICK_BLOCK) {
                commands = profile.rightClickCommands();
            } else {
                commands = profile.leftClickCommands();
            }
            if (!commands.isEmpty()) {
                executorState.mainThreadExec().execute(() -> {
                    for (String command : commands) {
                        player.performCommand(command);
                    }
                });
            }
        });
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSignOpen(@NotNull PlayerOpenSignEvent event) {
        Sign sign = event.getSign();
        Block block = sign.getBlock();
        SignCache.SignCacheEntry entry = signCache.get(
                block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        if (entry != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(@NotNull BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!(block.getState(false) instanceof Sign)) {
            return;
        }
        UUID worldId = block.getWorld().getUID();
        int blockX = block.getX();
        int blockY = block.getY();
        int blockZ = block.getZ();
        SignCache.SignCacheEntry entry = signCache.remove(worldId, blockX, blockY, blockZ);
        if (entry != null) {
            Player player = event.getPlayer();
            executorState.dbExec().execute(() -> {
                try (SqlSessionWrapper session = database.openSession(true)) {
                    session.realtySignMapper().deleteByPosition(worldId, blockX, blockY, blockZ);
                }
                player.sendMessage(messages.messageFor(MessageKeys.SIGN_REMOVE_SUCCESS));
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(@NotNull ChunkLoadEvent event) {
        int chunkX = event.getChunk().getX();
        int chunkZ = event.getChunk().getZ();
        executorState.dbExec().execute(() -> signTextApplicator.loadAndApplyChunkSigns(
                event.getWorld(), chunkX, chunkZ, executorState.mainThreadExec()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(@NotNull ChunkUnloadEvent event) {
        signCache.evictChunk(event.getWorld().getUID(),
                event.getChunk().getX(), event.getChunk().getZ());
    }
}
