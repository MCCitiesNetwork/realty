package io.github.md5sha256.realty.api;

import org.jetbrains.annotations.NotNull;

import java.text.DecimalFormat;

public final class CurrencyFormatter {

    private static final DecimalFormat FORMAT = new DecimalFormat("#,##0.00");

    private CurrencyFormatter() {}

    public static @NotNull String format(double amount) {
        return FORMAT.format(amount);
    }
}
