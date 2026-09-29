package io.github.md5sha256.realty.economy;

import io.github.md5sha256.realty.api.Party;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Abstraction over the server economy (Vault or Treasury).
 * All methods are synchronous and may be called from any thread,
 * though callers should avoid the main thread for Treasury which performs I/O.
 */
public interface EconomyProvider {

    /**
     * Returns the current balance of the account a payment by {@code party} would use.
     * Returns {@code 0.0} if the party has no usable account.
     */
    double getBalance(@NotNull Party party);

    /**
     * Transfers {@code amount} from {@code from} to {@code to}.
     * The {@code ledgerMessage} is a human-readable description that appears
     * in the transaction history (e.g. "Plot Purchase: my_plot").
     * {@code initiator} is the player whose action caused the payment, or
     * {@code null} for a scheduled event.
     * <p>
     * The implementation is responsible for atomicity: if the deposit step
     * fails, the withdrawal must be reversed before returning {@link PaymentResult.Failure}.
     */
    @NotNull PaymentResult transfer(@NotNull Party from, @NotNull Party to, double amount,
                                    @NotNull String ledgerMessage, @Nullable UUID initiator);

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
