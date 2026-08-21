package io.github.md5sha256.realty.api;

import org.jetbrains.annotations.NotNull;

import java.text.DecimalFormat;

public final class CurrencyFormatter {

    // DecimalFormat is not thread-safe; a shared instance corrupts output when commands format
    // concurrently on the async executors. Give each thread its own.
    private static final ThreadLocal<DecimalFormat> FORMAT =
            ThreadLocal.withInitial(() -> new DecimalFormat("#,##0.00"));

    private CurrencyFormatter() {}

    public static @NotNull String format(double amount) {
        return FORMAT.get().format(amount);
    }
}
