package io.github.md5sha256.realty.command.util;

/**
 * Inclusive lower bounds for {@link org.incendo.cloud.parser.standard.DoubleParser} ranges, aligned
 * with MariaDB {@code CHECK} constraints on money columns.
 *
 * <p>{@link #MIN_STRICTLY_POSITIVE} — use where the database requires {@code x > 0}. {@code DoubleParser}
 * ranges are inclusive on both ends, so the minimum must be strictly positive; {@code 0.01} matches
 * {@link io.github.md5sha256.realty.api.CurrencyFormatter} (two decimal places).</p>
 *
 * <p>For columns that allow zero (e.g. {@code currentPayment >= 0} on installment payments), use
 * {@code DoubleParser.doubleParser(0, Double.MAX_VALUE)} instead.</p>
 */
public final class ParseBounds {

    /** Inclusive minimum for amounts that must be {@code > 0} in the database. */
    public static final double MIN_STRICTLY_POSITIVE = 0.01d;

    private ParseBounds() {
    }
}
