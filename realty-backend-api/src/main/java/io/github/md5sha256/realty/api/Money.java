package io.github.md5sha256.realty.api;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Sums of money as Realty handles them: in whole cents. */
public final class Money {

    private Money() {}

    /** The amount rounded to cents, half up: {@code 0.004} is {@code 0}, {@code 0.005} is {@code 0.01}. */
    public static double cents(double amount) {
        return BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    /** {@code a - b}, exactly, in cents; both are rounded to cents first. */
    public static double minus(double a, double b) {
        return BigDecimal.valueOf(cents(a)).subtract(BigDecimal.valueOf(cents(b))).doubleValue();
    }

    /** {@code a + b}, exactly, in cents; both are rounded to cents first. */
    public static double plus(double a, double b) {
        return BigDecimal.valueOf(cents(a)).add(BigDecimal.valueOf(cents(b))).doubleValue();
    }

    /** Whether {@code a} is at least {@code b}, compared in whole cents. */
    public static boolean atLeast(double a, double b) {
        return BigDecimal.valueOf(cents(a)).compareTo(BigDecimal.valueOf(cents(b))) >= 0;
    }
}
