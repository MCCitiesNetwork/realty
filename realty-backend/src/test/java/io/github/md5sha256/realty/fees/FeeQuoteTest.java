package io.github.md5sha256.realty.fees;

import io.github.md5sha256.realty.api.FeeQuote;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeeQuoteTest {

    @Test
    void none_totalsEqualPrice() {
        FeeQuote quote = FeeQuote.none(1000);
        assertTrue(quote.lines().isEmpty());
        assertTrue(quote.buyerLines().isEmpty());
        assertTrue(quote.sellerLines().isEmpty());
        assertEquals(1000, quote.buyerTotal());
        assertEquals(1000, quote.sellerReceives());
    }
}
