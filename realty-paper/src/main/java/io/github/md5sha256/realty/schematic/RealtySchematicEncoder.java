package io.github.md5sha256.realty.schematic;

import io.github.md5sha256.realty.api.RealtySchematicFormat;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.DeflaterOutputStream;

/**
 * Writes a grid in Realty's own layout.
 *
 * <p>Not a WorldEdit schematic, and not convertible to one by any existing tool: the
 * bytes are served from a public endpoint, and a saved response should load nowhere.
 * The explorer carries the matching decoder, so this is an obstacle and not a lock.</p>
 *
 * <p>Touches neither WorldEdit nor the world, so it runs wherever the caller likes.</p>
 */
public final class RealtySchematicEncoder {

    private RealtySchematicEncoder() {
    }

    /**
     * @param dataVersion the Minecraft data version the block states were read under;
     *                    the explorer's renderer needs it to interpret them
     */
    public static byte @NotNull [] encode(@NotNull BlockGrid grid, int dataVersion) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(RealtySchematicFormat.header());
        try (DataOutputStream out = new DataOutputStream(new DeflaterOutputStream(bytes))) {
            out.writeInt(dataVersion);
            out.writeInt(grid.width());
            out.writeInt(grid.height());
            out.writeInt(grid.length());

            out.writeInt(grid.palette().size());
            for (BlockGrid.PaletteEntry entry : grid.palette()) {
                writeText(out, entry.state());
                writeText(out, entry.blockEntityId());
            }

            int[] cells = grid.cells();
            out.writeInt(countRuns(cells));
            int start = 0;
            for (int i = 1; i <= cells.length; i++) {
                if (i == cells.length || cells[i] != cells[start]) {
                    writeVarint(out, cells[start]);
                    writeVarint(out, i - start);
                    start = i;
                }
            }
        }
        return bytes.toByteArray();
    }

    private static int countRuns(int @NotNull [] cells) {
        int runs = 0;
        for (int i = 0; i < cells.length; i++) {
            if (i == 0 || cells[i] != cells[i - 1]) {
                runs++;
            }
        }
        return runs;
    }

    /** Length-prefixed UTF-8, written by hand because {@code writeUTF} is not quite UTF-8. */
    private static void writeText(@NotNull DataOutputStream out, @NotNull String text) throws IOException {
        byte[] encoded = text.getBytes(StandardCharsets.UTF_8);
        if (encoded.length > 0xffff) {
            throw new IOException("Block state too long to write: " + encoded.length + " bytes");
        }
        out.writeShort(encoded.length);
        out.write(encoded);
    }

    private static void writeVarint(@NotNull DataOutputStream out, int value) throws IOException {
        int remaining = value;
        while ((remaining & ~0x7f) != 0) {
            out.writeByte((remaining & 0x7f) | 0x80);
            remaining >>>= 7;
        }
        out.writeByte(remaining);
    }
}
