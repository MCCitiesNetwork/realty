package io.github.md5sha256.realty.schematic;

import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockState;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Reads a filled clipboard into a {@link BlockGrid}.
 *
 * <p>This is the last place a WorldEdit type or a world coordinate exists. Everything
 * after it works on the grid, where positions are counted from the capture's own corner.</p>
 *
 * <p>Reads the clipboard, never the world, so it is safe off the main thread.</p>
 */
final class ClipboardGrids {

    /** Drawn identically, and the cull needs to ask only "is this air". */
    private static final Set<String> AIR =
            Set.of("minecraft:air", "minecraft:cave_air", "minecraft:void_air");

    /** Seen through from any side and to any depth, as air is: a pond shows its bed. */
    private static final Set<String> FLUIDS = Set.of("minecraft:water", "minecraft:lava");

    private ClipboardGrids() {
    }

    /**
     * @param hides whether a block hides what is behind it. Asked once per distinct block
     *              state, not once per cell.
     */
    static @NotNull BlockGrid fromClipboard(@NotNull Clipboard clipboard,
                                                   @NotNull Predicate<BlockState> hides) {
        Region region = clipboard.getRegion();
        BlockVector3 corner = region.getMinimumPoint();
        int width = region.getWidth();
        int height = region.getHeight();
        int length = region.getLength();

        List<BlockGrid.PaletteEntry> palette = new ArrayList<>();
        Map<BlockGrid.PaletteEntry, Integer> numbered = new HashMap<>();
        palette.add(BlockGrid.PaletteEntry.AIR);
        numbered.put(BlockGrid.PaletteEntry.AIR, BlockGrid.AIR);
        // Printing a state and reducing it builds strings, and a capture is up to a
        // million cells of a few dozen distinct blocks. Blocks without a block entity
        // are looked up by state and never printed twice.
        Map<BlockState, Integer> plain = new HashMap<>();

        int[] cells = new int[Math.multiplyExact(Math.multiplyExact(width, height), length)];
        int index = 0;
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < length; z++) {
                for (int y = 0; y < height; y++) {
                    BaseBlock block = clipboard.getFullBlock(corner.add(x, y, z));
                    BlockState state = block.toImmutableState();
                    String blockEntityId = block.getNbtReference() == null ? "" : block.getNbtId();
                    Integer known = blockEntityId.isEmpty() ? plain.get(state) : null;
                    if (known == null) {
                        BlockGrid.PaletteEntry entry = entryFor(state, blockEntityId, hides);
                        known = numbered.computeIfAbsent(entry, added -> {
                            palette.add(added);
                            return palette.size() - 1;
                        });
                        if (blockEntityId.isEmpty()) {
                            plain.put(state, known);
                        }
                    }
                    cells[index++] = known;
                }
            }
        }
        return new BlockGrid(width, height, length, palette, cells);
    }

    private static @NotNull BlockGrid.PaletteEntry entryFor(@NotNull BlockState state,
                                                            @NotNull String blockEntityId,
                                                            @NotNull Predicate<BlockState> hides) {
        String id = state.getBlockType().id();
        if (AIR.contains(id)) {
            return BlockGrid.PaletteEntry.AIR;
        }
        String printed = state.getAsString();
        BlockGrid.Sight sight;
        if (FLUIDS.contains(id)) {
            sight = BlockGrid.Sight.OPEN;
        } else if (hides.test(state)) {
            sight = BlockGrid.Sight.SOLID;
        } else {
            // Read from the state in full, before it is reduced: which way a door
            // stands is decided by properties the reduction has no reason to keep.
            sight = BlockGrid.Sight.covering(FaceCover.of(printed));
        }
        return new BlockGrid.PaletteEntry(VisualState.reduce(printed), blockEntityId, sight);
    }
}
