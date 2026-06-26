package io.github.md5sha256.realty.wand;

import com.sk89q.worldedit.math.BlockVector3;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Holds each player's in-progress {@link WandSelection}.
 *
 * <p>A plain {@link HashMap} is intentional: every access happens on the server main thread —
 * point capture in the interact event, the particle render task, and dialog open. The dialog's
 * single async stage captures the region before leaving the main thread, so this map is never
 * touched concurrently.</p>
 */
public final class SubregionWandManager {

    private final Map<UUID, WandSelection> selections = new HashMap<>();

    /**
     * Appends a marked footprint point for the player. If the player's stored selection is in a
     * different world (they moved worlds), it is reset to the new world first.
     */
    public @NotNull WandSelection addPoint(@NotNull UUID playerId, @NotNull World world,
                                           @NotNull BlockVector3 point) {
        WandSelection selection = selections.get(playerId);
        if (selection == null || selection.world() != world) {
            selection = new WandSelection(world);
            selections.put(playerId, selection);
        }
        selection.addPoint(point);
        return selection;
    }

    /**
     * Removes the player's most recently marked point. Returns the selection (possibly now empty),
     * or {@code null} if there was nothing to remove.
     */
    public @Nullable WandSelection removeLastPoint(@NotNull UUID playerId) {
        WandSelection selection = selections.get(playerId);
        if (selection == null || !selection.removeLastPoint()) {
            return null;
        }
        return selection;
    }

    public @Nullable WandSelection get(@NotNull UUID playerId) {
        return selections.get(playerId);
    }

    public boolean isComplete(@NotNull UUID playerId) {
        WandSelection selection = selections.get(playerId);
        return selection != null && selection.isComplete();
    }

    public void clear(@NotNull UUID playerId) {
        selections.remove(playerId);
    }
}
