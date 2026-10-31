package io.github.md5sha256.realty.api;

import org.jetbrains.annotations.NotNull;

/**
 * One fee on a sale.
 *
 * @param source    the levy name, or another label for where the fee comes from.
 * @param recipient who receives the fee.
 * @param side      who pays it.
 * @param percent   the percentage of the price.
 * @param amount    the amount charged.
 */
public record FeeLine(
        @NotNull String source,
        @NotNull Party recipient,
        @NotNull FeeSide side,
        double percent,
        double amount
) {}
