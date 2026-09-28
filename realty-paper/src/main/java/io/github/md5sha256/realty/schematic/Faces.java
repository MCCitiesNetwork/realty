package io.github.md5sha256.realty.schematic;

/**
 * The six faces of a cell, as bits that can be combined. Directions are the game's: east
 * is towards greater x, up towards greater y, south towards greater z.
 */
final class Faces {

    static final int EAST = 1;
    static final int WEST = 1 << 1;
    static final int UP = 1 << 2;
    static final int DOWN = 1 << 3;
    static final int SOUTH = 1 << 4;
    static final int NORTH = 1 << 5;

    static final int NONE = 0;
    static final int ALL = EAST | WEST | UP | DOWN | SOUTH | NORTH;

    /** Every face in turn, in the same order as {@link #STEPS}. */
    static final int[] EACH = {EAST, WEST, UP, DOWN, SOUTH, NORTH};

    /** The step from a cell to its neighbour across each face of {@link #EACH}, as x, y, z. */
    static final int[][] STEPS = {
            {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };

    private Faces() {
    }

    /** The face named {@code east}, {@code north} and so on in a block state, or none. */
    static int named(String direction) {
        return switch (direction) {
            case "east" -> EAST;
            case "west" -> WEST;
            case "up" -> UP;
            case "down" -> DOWN;
            case "south" -> SOUTH;
            case "north" -> NORTH;
            default -> NONE;
        };
    }

    static int opposite(int face) {
        return switch (face) {
            case EAST -> WEST;
            case WEST -> EAST;
            case UP -> DOWN;
            case DOWN -> UP;
            case SOUTH -> NORTH;
            case NORTH -> SOUTH;
            default -> NONE;
        };
    }

    /** A quarter turn to the right, seen from above: east, south, west, north, east. */
    static int clockwise(int face) {
        return switch (face) {
            case EAST -> SOUTH;
            case SOUTH -> WEST;
            case WEST -> NORTH;
            case NORTH -> EAST;
            default -> NONE;
        };
    }
}
