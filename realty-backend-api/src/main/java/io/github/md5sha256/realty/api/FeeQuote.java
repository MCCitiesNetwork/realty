package io.github.md5sha256.realty.api;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The fees on a sale at a given price.
 *
 * @param price          the sale price.
 * @param lines          every fee line.
 * @param buyerTotal     what the buyer pays in all.
 * @param sellerReceives what the seller receives after fees.
 */
public record FeeQuote(
        double price,
        @NotNull List<FeeLine> lines,
        double buyerTotal,
        double sellerReceives
) {

    public FeeQuote {
        lines = List.copyOf(lines);
    }

    /** @return a quote with no fees: both totals equal the price. */
    public static @NotNull FeeQuote none(double price) {
        return new FeeQuote(price, List.of(), price, price);
    }

    public @NotNull List<FeeLine> buyerLines() {
        return lines.stream().filter(l -> l.side() == FeeSide.BUYER).toList();
    }

    public @NotNull List<FeeLine> sellerLines() {
        return lines.stream().filter(l -> l.side() == FeeSide.SELLER).toList();
    }
}
