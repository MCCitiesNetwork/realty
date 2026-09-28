package io.github.md5sha256.realty.schematic;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Removes every block that cannot be seen from outside the captured box.
 *
 * <p>The preview's camera is held outside the plot, so nothing removed here was ever
 * going to be drawn. What it changes is the bytes: a capture is served from a public
 * endpoint, and a build pasted back from one is a shell with nothing in it.</p>
 *
 * <p>The view starts on all six faces of the box and spreads inward. Open air carries it
 * in every direction. A block that hides nothing -- glass, a stair, a torch, a leaf --
 * carries it into the blocks beside it, but never into the air beside it. So the wall
 * behind a torch is kept and the ground under a flower is kept, while the room behind a
 * window is not: the view reaches the block pressed against the glass and stops.</p>
 */
public final class ShellCull {

    private static final int[][] NEIGHBOURS = {
            {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };

    private ShellCull() {
    }

    /**
     * @return a new grid holding only what is visible, with a palette rebuilt from what
     *         remains. The palette is rebuilt because it is served too, and a block type
     *         that survives only in the palette still says what was inside.
     */
    public static @NotNull BlockGrid hollow(@NotNull BlockGrid grid) {
        boolean[] reached = reach(grid);
        int[] cells = grid.cells();
        List<BlockGrid.PaletteEntry> palette = grid.palette();

        int[] renumbered = new int[palette.size()];
        Arrays.fill(renumbered, -1);
        renumbered[BlockGrid.AIR] = BlockGrid.AIR;
        List<BlockGrid.PaletteEntry> kept = new ArrayList<>();
        kept.add(palette.get(BlockGrid.AIR));

        int[] visible = new int[cells.length];
        for (int i = 0; i < cells.length; i++) {
            int block = reached[i] ? cells[i] : BlockGrid.AIR;
            if (renumbered[block] < 0) {
                renumbered[block] = kept.size();
                kept.add(palette.get(block));
            }
            visible[i] = renumbered[block];
        }
        return new BlockGrid(grid.width(), grid.height(), grid.length(), kept, visible);
    }

    private static boolean @NotNull [] reach(@NotNull BlockGrid grid) {
        int width = grid.width();
        int height = grid.height();
        int length = grid.length();
        int[] cells = grid.cells();
        List<BlockGrid.PaletteEntry> palette = grid.palette();

        boolean[] reached = new boolean[cells.length];
        // A cell is queued once, when it is first reached, so the queue cannot outgrow
        // the grid. An array rather than a deque: a million boxed integers is a real cost.
        int[] queue = new int[cells.length];
        int head = 0;
        int tail = 0;

        for (int x = 0; x < width; x++) {
            for (int z = 0; z < length; z++) {
                for (int y = 0; y < height; y++) {
                    boolean onAFace = x == 0 || y == 0 || z == 0
                            || x == width - 1 || y == height - 1 || z == length - 1;
                    if (onAFace) {
                        int index = grid.index(x, y, z);
                        reached[index] = true;
                        queue[tail++] = index;
                    }
                }
            }
        }

        while (head < tail) {
            int index = queue[head++];
            boolean air = cells[index] == BlockGrid.AIR;
            if (!air && palette.get(cells[index]).occluding()) {
                continue;
            }
            int y = index % height;
            int z = (index / height) % length;
            int x = index / (height * length);
            for (int[] step : NEIGHBOURS) {
                int nx = x + step[0];
                int ny = y + step[1];
                int nz = z + step[2];
                if (nx < 0 || ny < 0 || nz < 0 || nx >= width || ny >= height || nz >= length) {
                    continue;
                }
                int neighbour = grid.index(nx, ny, nz);
                if (reached[neighbour]) {
                    continue;
                }
                // Through a block that hides nothing, the view reaches the next block
                // and not the next room.
                if (!air && cells[neighbour] == BlockGrid.AIR) {
                    continue;
                }
                reached[neighbour] = true;
                queue[tail++] = neighbour;
            }
        }
        return reached;
    }
}
