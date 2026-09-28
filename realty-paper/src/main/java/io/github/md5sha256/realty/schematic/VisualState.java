package io.github.md5sha256.realty.schematic;

import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.StringJoiner;

/**
 * Drops the block-state properties that never change how a block is drawn.
 *
 * <p>A preview needs a stair's facing and a door's hinge. It does not need to know that
 * a leaf block was placed by a player, or that a door has a redstone signal. Leaving
 * those out costs the preview nothing, and a build pasted from a capture comes back
 * with leaves that decay and mechanisms in their resting state.</p>
 *
 * <p>The list is short on purpose. A property is here only if it changes no vanilla
 * model and no resource pack is known to key on it. Note blocks are left untouched for
 * that second reason: custom-block plugins hang their textures on note block states.
 * {@code waterlogged} is kept because the water is drawn.</p>
 */
final class VisualState {

    /** Never visible, on any block that has them. */
    private static final Set<String> NEVER_DRAWN =
            Set.of("persistent", "distance", "occupied", "stage", "unstable", "enabled");

    private VisualState() {
    }

    /**
     * @param blockState a state as WorldEdit prints it, such as
     *                   {@code minecraft:oak_leaves[distance=1,persistent=true]}
     * @return the same state without the properties that are never drawn; the brackets
     *         go too when nothing is left inside them
     */
    static @NotNull String reduce(@NotNull String blockState) {
        int open = blockState.indexOf('[');
        if (open < 0 || !blockState.endsWith("]")) {
            return blockState;
        }
        String id = blockState.substring(0, open);
        StringJoiner kept = new StringJoiner(",", "[", "]");
        kept.setEmptyValue("");
        for (String property : blockState.substring(open + 1, blockState.length() - 1).split(",")) {
            int equals = property.indexOf('=');
            String name = equals < 0 ? property : property.substring(0, equals);
            if (!isDropped(id, name)) {
                kept.add(property);
            }
        }
        return id + kept;
    }

    private static boolean isDropped(@NotNull String id, @NotNull String name) {
        if (NEVER_DRAWN.contains(name)) {
            return true;
        }
        // A lever, a button and a rail are drawn from "powered"; these three are not.
        if (name.equals("powered")) {
            return id.endsWith("_door") || id.endsWith("_trapdoor") || id.endsWith("_fence_gate");
        }
        // A crafter's face changes when triggered. A dispenser's and a dropper's do not.
        if (name.equals("triggered")) {
            return id.equals("minecraft:dispenser") || id.equals("minecraft:dropper");
        }
        return false;
    }
}
