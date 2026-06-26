package io.github.md5sha256.realty.listener;

import com.sk89q.worldedit.math.BlockVector3;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.localisation.MessageKeys;
import io.github.md5sha256.realty.wand.SubregionWand;
import io.github.md5sha256.realty.wand.SubregionWandManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Drives the subregion selection wand: right-click marks a corner, left-click undoes the last one.
 * Two marked points make a box; three or more make a polygon. A particle outline previews the
 * footprint while the wand is held. Opening the height/create dialog is done via
 * {@code /realty subregion confirm}.
 */
public final class SubregionWandListener implements Listener {

    private static final long RENDER_PERIOD_TICKS = 10L;
    private static final int MAX_POINTS_PER_EDGE = 32;

    private final SubregionWand wand;
    private final SubregionWandManager wandManager;
    private final MessageContainer messages;

    public SubregionWandListener(@NotNull Plugin plugin,
                                 @NotNull SubregionWand wand,
                                 @NotNull SubregionWandManager wandManager,
                                 @NotNull MessageContainer messages) {
        this.wand = wand;
        this.wandManager = wandManager;
        this.messages = messages;
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::renderSelections,
                RENDER_PERIOD_TICKS, RENDER_PERIOD_TICKS);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPlayerInteract(@NotNull PlayerInteractEvent event) {
        if (event.getHand() == EquipmentSlot.OFF_HAND) {
            return;
        }
        ItemStack item = event.getItem();
        if (!wand.isWand(item)) {
            return;
        }
        Player player = event.getPlayer();
        Action action = event.getAction();

        if (action == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            addPoint(player, event.getClickedBlock());
        } else if (action == Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true);
            undoPoint(player);
        }
    }

    @EventHandler
    public void onQuit(@NotNull PlayerQuitEvent event) {
        wandManager.clear(event.getPlayer().getUniqueId());
    }

    private void addPoint(@NotNull Player player, Block block) {
        if (block == null) {
            return;
        }
        // Feedback is the particle outline + action bar; no chat spam per corner.
        BlockVector3 point = BlockVector3.at(block.getX(), block.getY(), block.getZ());
        wandManager.addPoint(player.getUniqueId(), block.getWorld(), point);
    }

    private void undoPoint(@NotNull Player player) {
        wandManager.removeLastPoint(player.getUniqueId());
    }

    private void renderSelections() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!wand.isWand(player.getInventory().getItemInMainHand())) {
                continue;
            }
            SubregionWandManager.WandSelection selection = wandManager.get(player.getUniqueId());
            int size = selection == null ? 0 : selection.size();
            // Keep a hint on the action bar while the wand is held (refreshed before it fades).
            player.sendActionBar(messages.messageFor(size >= 2
                    ? MessageKeys.SUBREGION_HINT_READY : MessageKeys.SUBREGION_HINT_PLACE));
            if (selection == null || !selection.isComplete()
                    || selection.world() != player.getWorld()) {
                continue;
            }
            // Before the height is set, preview the footprint as a flat ring at the marked level.
            int markedLevel = selection.heightSet() ? 0 : selection.minPointY();
            double floor = selection.heightSet() ? selection.floorY() : markedLevel;
            double ceiling = (selection.heightSet() ? selection.ceilingY() : markedLevel) + 1;
            List<BlockVector3> points = selection.points();
            if (selection.isPolygon()) {
                drawPolygon(player, selection.world(), points, floor, ceiling);
            } else {
                drawBox(player, selection.world(), points.get(0), points.get(1), floor, ceiling);
            }
        }
    }

    private void drawBox(@NotNull Player player, @NotNull World world,
                         @NotNull BlockVector3 a, @NotNull BlockVector3 b,
                         double floor, double ceiling) {
        // Outer shell: a block at x spans [x, x+1), so the horizontal edges run min .. max+1.
        double minX = Math.min(a.x(), b.x());
        double minZ = Math.min(a.z(), b.z());
        double maxX = Math.max(a.x(), b.x()) + 1;
        double maxZ = Math.max(a.z(), b.z()) + 1;

        double[] xs = {minX, maxX};
        double[] ys = {floor, ceiling};
        double[] zs = {minZ, maxZ};

        for (double y : ys) {
            for (double z : zs) {
                edge(player, world, minX, y, z, maxX, y, z);
            }
        }
        for (double x : xs) {
            for (double z : zs) {
                edge(player, world, x, floor, z, x, ceiling, z);
            }
        }
        for (double x : xs) {
            for (double y : ys) {
                edge(player, world, x, y, minZ, x, y, maxZ);
            }
        }
    }

    private void drawPolygon(@NotNull Player player, @NotNull World world,
                             @NotNull List<BlockVector3> points, double minY, double maxY) {
        int count = points.size();
        for (int i = 0; i < count; i++) {
            BlockVector3 current = points.get(i);
            BlockVector3 next = points.get((i + 1) % count);
            double cx = current.x() + 0.5;
            double cz = current.z() + 0.5;
            double nx = next.x() + 0.5;
            double nz = next.z() + 0.5;
            // Vertical pillar at each vertex.
            edge(player, world, cx, minY, cz, cx, maxY, cz);
            // Footprint edges along the floor and ceiling.
            edge(player, world, cx, minY, cz, nx, minY, nz);
            edge(player, world, cx, maxY, cz, nx, maxY, nz);
        }
    }

    private void edge(@NotNull Player player, @NotNull World world,
                      double x1, double y1, double z1, double x2, double y2, double z2) {
        double length = Math.max(Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1)), Math.abs(z2 - z1));
        int steps = (int) Math.min(MAX_POINTS_PER_EDGE, Math.max(1, length));
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            Location loc = new Location(world,
                    x1 + (x2 - x1) * t, y1 + (y2 - y1) * t, z1 + (z2 - z1) * t);
            player.spawnParticle(Particle.HAPPY_VILLAGER, loc, 1, 0, 0, 0, 0);
        }
    }
}
