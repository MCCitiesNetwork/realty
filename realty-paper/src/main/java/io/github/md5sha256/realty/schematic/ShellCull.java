package io.github.md5sha256.realty.schematic;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Removes the blocks that the view from outside the captured box does not reach.
 *
 * <p>A capture is served from a public endpoint. This is what keeps the inside of a
 * closed building out of it, so that a build pasted back from one is a shell.</p>
 *
 * <p>The view starts on the four sides and the top of the box and spreads inward through
 * open cells: air, and water or lava. Every block it touches is kept.</p>
 *
 * <p>A block that is kept and is less than a full cube -- glass, a torch, a fence, a
 * slab -- may show what is beside it. The view has to be able to enter its cell, through
 * a face the block does not cover, from an open cell it has reached. Then each block
 * beside it is kept too, except across a face the block covers. So the ground under a
 * flower is kept, and the block pressed against a window. The room under a slab roof is
 * not: a slab covers the face beneath it.</p>
 *
 * <p>The view stops at those blocks. It does not travel on through them, whatever they
 * are, and it never enters the air beside them. Letting it run on carried it across a
 * carpeted floor and down a fence post into a sealed room.</p>
 *
 * <p>The bottom of the box is not a way in. It is the ground the capturing player stood
 * on, and the preview's camera stays above it.</p>
 *
 * <p>Two things this does not do. It follows open air wherever it leads, so one gap in a
 * wall or a roof brings in everything the air behind it connects to. And it counts the
 * sides of the box as seen, so a room that the box cuts through is kept as far as the
 * air in it runs.</p>
 */
final class ShellCull {

    private ShellCull() {
    }

    /**
     * @return a new grid holding only what the view reaches, with a palette rebuilt from
     *         what remains. The palette is rebuilt because it is served too, and a block
     *         type that survives only in the palette still says what was inside.
     */
    static @NotNull BlockGrid hollow(@NotNull BlockGrid grid) {
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
        // Where the open view has been. Kept apart from "seen" because a block shown by
        // the block beside it is seen, and the view has still not been there.
        boolean[] reached = new boolean[cells.length];
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
                        reached[index] = true;
                        touched[count++] = index;
                    }
                }
            }
        }

        // First, everything the open view touches. Only open cells pass it on.
        for (int next = 0; next < count; next++) {
            int index = touched[next];
            seen[index] = true;
            if (!palette.get(cells[index]).sight().carries()) {
                continue;
            }
            int y = index % height;
            int z = (index / height) % length;
            int x = index / (height * length);
            for (int[] step : Faces.STEPS) {
                int nx = x + step[0];
                int ny = y + step[1];
                int nz = z + step[2];
                if (nx < 0 || ny < 0 || nz < 0 || nx >= width || ny >= height || nz >= length) {
                    continue;
                }
                int neighbour = grid.index(nx, ny, nz);
                if (!reached[neighbour]) {
                    reached[neighbour] = true;
                    touched[count++] = neighbour;
                }
            }
        }

        // Then what each of those blocks shows beside it. What is found here is marked
        // seen and not reached, so it shows nothing in its turn.
        for (int next = 0; next < count; next++) {
            int index = touched[next];
            BlockGrid.Sight sight = palette.get(cells[index]).sight();
            if (sight.carries() || sight.covers() == Faces.ALL) {
                continue;
            }
            int y = index % height;
            int z = (index / height) % length;
            int x = index / (height * length);

            boolean entered = false;
            for (int face = 0; face < Faces.EACH.length && !entered; face++) {
                if ((sight.covers() & Faces.EACH[face]) != 0) {
                    continue;
                }
                int nx = x + Faces.STEPS[face][0];
                int ny = y + Faces.STEPS[face][1];
                int nz = z + Faces.STEPS[face][2];
                if (nx < 0 || nz < 0 || nx >= width || nz >= length || ny >= height) {
                    entered = true;
                } else if (ny >= 0) {
                    int neighbour = grid.index(nx, ny, nz);
                    entered = reached[neighbour] && palette.get(cells[neighbour]).sight().carries();
                }
            }
            if (!entered) {
                continue;
            }

            for (int face = 0; face < Faces.EACH.length; face++) {
                if ((sight.covers() & Faces.EACH[face]) != 0) {
                    continue;
                }
                int nx = x + Faces.STEPS[face][0];
                int ny = y + Faces.STEPS[face][1];
                int nz = z + Faces.STEPS[face][2];
                if (nx < 0 || ny < 0 || nz < 0 || nx >= width || ny >= height || nz >= length) {
                    continue;
                }
                int neighbour = grid.index(nx, ny, nz);
                // The block beside it, never the air beside it: air that is seen is air
                // the view travels through, and that is how it would reach the next room.
                if (cells[neighbour] != BlockGrid.AIR) {
                    seen[neighbour] = true;
                }
            }
        }
        return seen;
    }
}
