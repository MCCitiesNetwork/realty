package io.github.md5sha256.realty.economy;

/**
 * Result of an economy transfer operation.
 */
public sealed interface PaymentResult {

    record Success() implements PaymentResult {}

    record Failure(String errorMessage) implements PaymentResult {}
}
