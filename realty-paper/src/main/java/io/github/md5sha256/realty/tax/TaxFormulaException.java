package io.github.md5sha256.realty.tax;

/** Thrown when a property-tax formula string cannot be parsed. */
public final class TaxFormulaException extends IllegalArgumentException {
    public TaxFormulaException(String message) {
        super(message);
    }
}
