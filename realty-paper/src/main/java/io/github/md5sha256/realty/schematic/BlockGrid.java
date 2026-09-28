package io.github.md5sha256.realty.schematic;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A captured volume as plain numbers: a box of cells, each naming an entry in a palette.
 *
 * <p>Holds no WorldEdit type and no world coordinate. The cull and the encoder work on
 * this, which is what lets both be tested without a server, and what guarantees that a
 * position in the world has nowhere to travel.</p>
 *
 * <p>Cells run x outermost, then z, then y innermost. That is the order they are written
 * in, so encoding is one pass over the array.</p>
 *
 * @param width   extent along x
 * @param height  extent along y
 * @param length  extent along z
 * @param palette every distinct block in the grid; entry 0 is always air
 * @param cells   one palette index per cell
 */
public record BlockGrid(int width,
                        int height,
                        int length,
                        @NotNull List<PaletteEntry> palette,
                        int @NotNull [] cells) {

    /** The palette index of air, in every grid. */
    public static final int AIR = 0;

    /**
     * One distinct block.
     *
     * @param state         the block and the properties that shape it, as WorldEdit prints them
     * @param blockEntityId the block entity's id, or the empty string for none
     * @param occluding     whether the block hides what is behind it; used by the cull, never written
     */
    public record PaletteEntry(@NotNull String state, @NotNull String blockEntityId, boolean occluding) {

        public static final PaletteEntry AIR = new PaletteEntry("minecraft:air", "", false);
    }

    public BlockGrid {
        if (width <= 0 || height <= 0 || length <= 0) {
            throw new IllegalArgumentException(
                    "A grid needs a positive size, got " + width + "x" + height + "x" + length);
        }
        if (cells.length != Math.multiplyExact(Math.multiplyExact(width, height), length)) {
            throw new IllegalArgumentException(
                    cells.length + " cells cannot fill " + width + "x" + height + "x" + length);
        }
        if (palette.isEmpty() || !PaletteEntry.AIR.equals(palette.getFirst())) {
            throw new IllegalArgumentException("Palette entry 0 must be air");
        }
        palette = List.copyOf(palette);
    }

    /** Where the cell at these grid coordinates sits in {@link #cells()}. */
    public int index(int x, int y, int z) {
        return (x * this.length + z) * this.height + y;
    }
}
