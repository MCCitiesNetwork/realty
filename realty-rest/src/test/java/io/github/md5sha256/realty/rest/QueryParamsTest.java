package io.github.md5sha256.realty.rest;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class QueryParamsTest {

    @Test
    void decodesPercentEncodedSpaces() {
        Assertions.assertEquals("My World", QueryParams.plusAwareDecode("My%20World"));
    }

    @Test
    void decodesPlusAsASpace() {
        Assertions.assertEquals("My World", QueryParams.plusAwareDecode("My+World"));
    }

    @Test
    void decodesAFloodgateNameWithADotPrefixAndSpaces() {
        Assertions.assertEquals(".Cool Guy 123", QueryParams.plusAwareDecode(".Cool%20Guy%20123"));
        Assertions.assertEquals(".Cool Guy 123", QueryParams.plusAwareDecode(".Cool+Guy+123"));
    }

    @Test
    void leavesAPercentSignInAWorldNameIntact() {
        Assertions.assertEquals("100%", QueryParams.plusAwareDecode("100%25"));
    }

    @Test
    void leavesAnOrdinaryNameUnchanged() {
        Assertions.assertEquals("world_nether", QueryParams.plusAwareDecode("world_nether"));
    }
}
