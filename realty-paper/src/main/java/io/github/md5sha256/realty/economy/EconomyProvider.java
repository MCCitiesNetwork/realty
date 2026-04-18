package io.github.md5sha256.realty.economy;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Abstraction over the server economy (Vault or Treasury).
 * All methods are synchronous and may be called from any thread,
 * though callers should avoid the main thread for Treasury which performs I/O.
 */
public interface EconomyProvider {

    /**
     * Returns the current balance for the given player.
     * Returns {@code 0.0} if the player has no account.
     */
    double getBalance(@NotNull UUID playerId);

    /**
     * Transfers {@code amount} from {@code fromId} to {@code toId}.
     * The {@code ledgerMessage} is a human-readable description that appears
     * in the player's transaction history (e.g. "Plot Purchase: my_plot").
     * <p>
     * The implementation is responsible for atomicity: if the deposit step
     * fails, the withdrawal must be reversed before returning {@link PaymentResult.Failure}.
     */
    @NotNull PaymentResult transfer(@NotNull UUID fromId, @NotNull UUID toId,
                                     double amount, @NotNull String ledgerMessage);

    /**
     * Formats an amount as a currency string using this economy's currency symbol/format.
     */
    @NotNull String formatAmount(double amount);

    /**
     * Returns {@code true} if this provider has full ledger support (Treasury).
     * When {@code false} (Vault), ledger messages are silently discarded
     * and tax collection is unavailable.
     */
    boolean hasLedgerSupport();
}
