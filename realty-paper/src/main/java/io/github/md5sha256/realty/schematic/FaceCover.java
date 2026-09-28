package io.github.md5sha256.realty.schematic;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Which faces of its cell a block covers completely, for blocks that are less than a
 * full cube and yet cannot be seen through.
 *
 * <p>A slab in a roof lets nobody see the room under it. Neither does a stair, a carpet,
 * a trapdoor or a door. Treated as glass is treated, each of them showed the block
 * behind it, so a slab roof published whatever stood on the top floor.</p>
 *
 * <p>The server's block data cannot answer this: it says whether a block is a full cube
 * and nothing about its faces. So it is read from the block's own state. The shapes are
 * the vanilla ones. A block not listed here covers nothing, which keeps a block behind
 * it that need not have been kept.</p>
 */
final class FaceCover {

    /** A sixteenth short of a full block. Nothing under one or behind one can be seen. */
    private static final Set<String> AS_GOOD_AS_FULL = Set.of("minecraft:farmland", "minecraft:dirt_path");

    private FaceCover() {
    }

    /**
     * @param blockState a state as WorldEdit prints it, in full
     * @return the faces covered, as {@link Faces} bits; {@link Faces#NONE} for a block
     *         that covers none, which is most of them
     */
    static int of(@NotNull String blockState) {
        int open = blockState.indexOf('[');
        String id = open < 0 ? blockState : blockState.substring(0, open);
        Map<String, String> properties = properties(blockState, open);

        if (AS_GOOD_AS_FULL.contains(id)) {
            return Faces.ALL;
        }
        if (id.endsWith("_slab")) {
            return switch (properties.getOrDefault("type", "bottom")) {
                case "double" -> Faces.ALL;
                case "top" -> Faces.UP;
                default -> Faces.DOWN;
            };
        }
        if (id.endsWith("_stairs")) {
            // The tread lies on the floor or hangs from the ceiling, and the riser stands
            // at the back, which is the side the stair faces. A stair turned at a corner
            // has a back that is part open, so only a straight one is counted.
            int tread = "top".equals(properties.get("half")) ? Faces.UP : Faces.DOWN;
            boolean straight = "straight".equals(properties.getOrDefault("shape", "straight"));
            return straight ? tread | Faces.named(properties.getOrDefault("facing", "")) : tread;
        }
        if (id.endsWith("_carpet")) {
            return Faces.DOWN;
        }
        if (id.equals("minecraft:snow")) {
            return "8".equals(properties.get("layers")) ? Faces.ALL : Faces.DOWN;
        }
        if (id.endsWith("_trapdoor")) {
            if ("true".equals(properties.get("open"))) {
                // Open, it stands against the side it is hinged on: the one behind it.
                return Faces.opposite(Faces.named(properties.getOrDefault("facing", "")));
            }
            return "top".equals(properties.get("half")) ? Faces.UP : Faces.DOWN;
        }
        if (id.endsWith("_door")) {
            int facing = Faces.named(properties.getOrDefault("facing", ""));
            if (!"true".equals(properties.get("open"))) {
                // Shut, it stands on the edge of its cell nearest whoever placed it.
                return Faces.opposite(facing);
            }
            // Open, it has swung a quarter turn about its hinge.
            return "right".equals(properties.get("hinge"))
                    ? Faces.clockwise(facing)
                    : Faces.opposite(Faces.clockwise(facing));
        }
        return Faces.NONE;
    }

    private static @NotNull Map<String, String> properties(@NotNull String blockState, int open) {
        Map<String, String> properties = new HashMap<>();
        if (open < 0 || !blockState.endsWith("]")) {
            return properties;
        }
        for (String property : blockState.substring(open + 1, blockState.length() - 1).split(",")) {
            int equals = property.indexOf('=');
            if (equals > 0) {
                properties.put(property.substring(0, equals), property.substring(equals + 1));
            }
        }
        return properties;
    }
}
