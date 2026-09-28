package io.github.md5sha256.realty.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * The first bytes of every schematic Realty stores, and the check that recognises them.
 *
 * <p>Captures are written in a layout of Realty's own rather than as a WorldEdit
 * schematic, so that a file saved from the public endpoint loads in no existing tool.
 * The plugin writes this header, and the REST service refuses to serve a row without
 * it. That refusal is what keeps a capture from an older plugin, still sitting in a
 * shared database, from being handed out as a WorldEdit file.</p>
 *
 * <p>This is a format, not a secret. The decoder ships to every browser.</p>
 */
public final class RealtySchematicFormat {

    /** The layout version this build writes and reads. */
    public static final int VERSION = 1;

    private static final byte[] MAGIC = {'R', 'L', 'T', 'Y'};

    /** The magic plus one version byte. The compressed body starts here. */
    public static final int HEADER_LENGTH = MAGIC.length + 1;

    private RealtySchematicFormat() {
    }

    /** A fresh copy of the header, so no caller can alter the one every other caller gets. */
    public static byte @NotNull [] header() {
        byte[] header = Arrays.copyOf(MAGIC, HEADER_LENGTH);
        header[MAGIC.length] = (byte) VERSION;
        return header;
    }

    /**
     * Whether {@code data} opens with the header of a version this build understands.
     *
     * <p>Says nothing about the body. A row that passes here can still be corrupt, and
     * the decoder is what finds that out.</p>
     */
    public static boolean isReadable(byte @Nullable [] data) {
        if (data == null || data.length < HEADER_LENGTH) {
            return false;
        }
        for (int i = 0; i < MAGIC.length; i++) {
            if (data[i] != MAGIC[i]) {
                return false;
            }
        }
        return data[MAGIC.length] == VERSION;
    }
}
