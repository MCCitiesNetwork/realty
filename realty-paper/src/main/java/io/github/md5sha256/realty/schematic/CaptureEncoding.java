package io.github.md5sha256.realty.schematic;

import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.world.block.BlockState;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.function.Predicate;

/**
 * Everything between a filled clipboard and the bytes that are stored, in one place.
 *
 * <p>The only way in. The reader, the cull and the encoder are visible inside this
 * package and nowhere else, so the capture command cannot call one and leave out
 * another. A capture that skipped the cull would still encode, still store and still
 * draw, and would publish the inside of the building.</p>
 */
public final class CaptureEncoding {

    private CaptureEncoding() {
    }

    /**
     * Reads the clipboard into a grid, removes what cannot be seen from outside, and
     * writes what is left in Realty's own format.
     *
     * <p>Touches the clipboard and never the world, so it runs off the main thread.</p>
     *
     * @param hides       whether a block hides what is behind it
     * @param dataVersion the Minecraft data version the block states were read under
     */
    public static byte @NotNull [] encode(@NotNull Clipboard clipboard,
                                          @NotNull Predicate<BlockState> hides,
                                          int dataVersion) throws IOException {
        BlockGrid captured = ClipboardGrids.fromClipboard(clipboard, hides);
        return RealtySchematicEncoder.encode(ShellCull.hollow(captured), dataVersion);
    }
}
