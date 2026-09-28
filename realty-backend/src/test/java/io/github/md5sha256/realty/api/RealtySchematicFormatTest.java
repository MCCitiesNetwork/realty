package io.github.md5sha256.realty.api;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class RealtySchematicFormatTest {

    @Test
    void theHeaderIsTheMagicThenTheVersion() {
        Assertions.assertArrayEquals(new byte[]{'R', 'L', 'T', 'Y', 1}, RealtySchematicFormat.header());
        Assertions.assertEquals(5, RealtySchematicFormat.HEADER_LENGTH);
    }

    @Test
    void theHeaderIsACopySoACallerCannotCorruptIt() {
        RealtySchematicFormat.header()[0] = 0;
        Assertions.assertEquals('R', RealtySchematicFormat.header()[0]);
    }

    @Test
    void bytesThatStartWithTheHeaderAreReadable() {
        Assertions.assertTrue(RealtySchematicFormat.isReadable(new byte[]{'R', 'L', 'T', 'Y', 1, 9, 9}));
    }

    @Test
    void aWorldEditSchematicIsNotReadable() {
        // Sponge schematics are gzipped NBT, so they open with the gzip magic.
        Assertions.assertFalse(RealtySchematicFormat.isReadable(new byte[]{0x1f, (byte) 0x8b, 8, 0, 0, 0}));
    }

    @Test
    void aVersionThisBuildDoesNotKnowIsNotReadable() {
        Assertions.assertFalse(RealtySchematicFormat.isReadable(new byte[]{'R', 'L', 'T', 'Y', 2, 9}));
    }

    @Test
    void nullEmptyAndTruncatedBytesAreNotReadable() {
        Assertions.assertFalse(RealtySchematicFormat.isReadable(null));
        Assertions.assertFalse(RealtySchematicFormat.isReadable(new byte[0]));
        Assertions.assertFalse(RealtySchematicFormat.isReadable(new byte[]{'R', 'L', 'T', 'Y'}));
    }
}
