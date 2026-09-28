package io.github.md5sha256.realty.schematic;

import io.github.md5sha256.realty.api.RealtySchematicFormat;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.InflaterInputStream;

/** Reads the format back, for tests only. Follows the layout in the plan, not the encoder. */
final class RealtySchematicTestDecoder {

    record Decoded(int dataVersion, int width, int height, int length,
                   List<String> states, List<String> blockEntityIds, int[] cells, int runCount) {
    }

    private RealtySchematicTestDecoder() {
    }

    static Decoded decode(byte[] bytes) throws IOException {
        if (!RealtySchematicFormat.isReadable(bytes)) {
            throw new IOException("not a Realty schematic");
        }
        ByteArrayInputStream body = new ByteArrayInputStream(bytes,
                RealtySchematicFormat.HEADER_LENGTH, bytes.length - RealtySchematicFormat.HEADER_LENGTH);
        try (DataInputStream in = new DataInputStream(new InflaterInputStream(body))) {
            int dataVersion = in.readInt();
            int width = in.readInt();
            int height = in.readInt();
            int length = in.readInt();
            int paletteSize = in.readInt();
            List<String> states = new ArrayList<>();
            List<String> blockEntityIds = new ArrayList<>();
            for (int i = 0; i < paletteSize; i++) {
                states.add(text(in));
                blockEntityIds.add(text(in));
            }
            int[] cells = new int[width * height * length];
            int runCount = in.readInt();
            int at = 0;
            for (int i = 0; i < runCount; i++) {
                int block = varint(in);
                int run = varint(in);
                for (int j = 0; j < run; j++) {
                    cells[at++] = block;
                }
            }
            if (at != cells.length) {
                throw new IOException("runs cover " + at + " of " + cells.length + " cells");
            }
            if (in.read() != -1) {
                throw new IOException("bytes after the last run");
            }
            return new Decoded(dataVersion, width, height, length, states, blockEntityIds, cells, runCount);
        }
    }

    private static String text(DataInputStream in) throws IOException {
        byte[] bytes = new byte[in.readUnsignedShort()];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static int varint(DataInputStream in) throws IOException {
        int value = 0;
        for (int shift = 0; shift <= 28; shift += 7) {
            int next = in.readUnsignedByte();
            value |= (next & 0x7f) << shift;
            if ((next & 0x80) == 0) {
                return value;
            }
        }
        throw new IOException("varint too long");
    }
}
