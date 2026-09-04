package io.github.md5sha256.realty.schematic;

import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Serialises a populated clipboard as Sponge Schematic v3.
 *
 * <p>Deliberately free of Bukkit and WorldGuard types, and of the world read that
 * fills the clipboard ({@code TickSlicedCopy} owns that), which leaves this testable
 * without a running server and safe to call off the main thread.</p>
 *
 * <p>Written against the WorldEdit API rather than FastAsyncWorldEdit's, so one
 * implementation serves either install -- FAWE provides the same classes.</p>
 */
public final class RegionSchematicWriter {

    private RegionSchematicWriter() {
    }

    /**
     * Serialises an already-populated clipboard.
     *
     * <p>Touches no world state, so unlike the copy that fills the clipboard this may
     * run off the main thread -- which is where the command calls it, since encoding is
     * the expensive half.</p>
     */
    public static byte @NotNull [] writeClipboard(@NotNull Clipboard clipboard) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ClipboardWriter writer = BuiltInClipboardFormat.SPONGE_V3_SCHEMATIC.getWriter(out)) {
            writer.write(clipboard);
        }
        return out.toByteArray();
    }
}
