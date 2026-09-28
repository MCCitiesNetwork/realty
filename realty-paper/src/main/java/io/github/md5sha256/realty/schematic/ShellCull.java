package io.github.md5sha256.realty.schematic;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Removes every block that cannot be seen from outside the captured box.
 *
 * <p>A capture is served from a public endpoint. What this leaves in it is what a
 * passer-by could already see, so a build pasted back from one is a shell.</p>
 *
 * <p>The view starts on the four sides and the top of the box and spreads inward through
 * open cells: air, and water or lava. Every block it touches is kept. A kept block that
 * hides nothing -- glass, a stair, a torch, a leaf -- also shows the one block directly
 * behind it, and the view stops there. So the wall behind a torch is kept and the ground
 * under a flower is kept, while the room behind a window is not: the block pressed
 * against the glass is seen and nothing beyond it.</p>
 *
 * <p>It stops after one block on purpose. Letting it run on through any chain of such
 * blocks carried it across a carpeted floor, down a fence post and through a flooded
 * room, and brought the inside of a sealed building out with it.</p>
 *
 * <p>The bottom of the box is not a way in. It is the ground the capturing player stood
 * on, and the camera stays above it. Counting it as seen kept the whole floor of every
 * building that stands level with the ground.</p>
 *
 * <p>This costs the preview something. Looking through a window shows an empty room
 * with no floor.</p>
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
        boolean[] seen = see(grid);
        int[] cells = grid.cells();
        List<BlockGrid.PaletteEntry> palette = grid.palette();

        int[] renumbered = new int[palette.size()];
        Arrays.fill(renumbered, -1);
        renumbered[BlockGrid.AIR] = BlockGrid.AIR;
        List<BlockGrid.PaletteEntry> kept = new ArrayList<>();
        kept.add(palette.get(BlockGrid.AIR));

        int[] visible = new int[cells.length];
        for (int i = 0; i < cells.length; i++) {
            int block = seen[i] ? cells[i] : BlockGrid.AIR;
            if (renumbered[block] < 0) {
                renumbered[block] = kept.size();
                kept.add(palette.get(block));
            }
            visible[i] = renumbered[block];
        }
        return new BlockGrid(grid.width(), grid.height(), grid.length(), kept, visible);
    }

    private static boolean @NotNull [] see(@NotNull BlockGrid grid) {
        int width = grid.width();
        int height = grid.height();
        int length = grid.length();
        int[] cells = grid.cells();
        List<BlockGrid.PaletteEntry> palette = grid.palette();

        boolean[] seen = new boolean[cells.length];
        // A cell is queued once, when the open view first touches it, so the queue cannot
        // outgrow the grid. An array, not a deque: a million boxed integers is a real cost.
        int[] touched = new int[cells.length];
        int count = 0;

        for (int x = 0; x < width; x++) {
            for (int z = 0; z < length; z++) {
                for (int y = 0; y < height; y++) {
                    boolean onASideOrTheTop = x == 0 || z == 0
                            || x == width - 1 || z == length - 1 || y == height - 1;
                    if (onASideOrTheTop) {
                        int index = grid.index(x, y, z);
                        seen[index] = true;
                        touched[count++] = index;
                    }
                }
            }
        }

        // First, everything the open view touches. Only open cells pass it on.
        for (int next = 0; next < count; next++) {
            int index = touched[next];
            if (palette.get(cells[index]).sight() != BlockGrid.Sight.OPEN) {
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
                if (!seen[neighbour]) {
                    seen[neighbour] = true;
                    touched[count++] = neighbour;
                }
            }
        }

        // Then the one block behind each see-through block the open view touched. What is
        // found here is not added to the list, so it shows nothing in its turn.
        for (int next = 0; next < count; next++) {
            int index = touched[next];
            if (palette.get(cells[index]).sight() != BlockGrid.Sight.SEE_THROUGH) {
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
                // The block behind, never the air behind: air that is seen is air the
                // view travels through, and that is how it would reach the next room.
                if (cells[neighbour] != BlockGrid.AIR) {
                    seen[neighbour] = true;
                }
            }
        }
        return seen;
    }
}
