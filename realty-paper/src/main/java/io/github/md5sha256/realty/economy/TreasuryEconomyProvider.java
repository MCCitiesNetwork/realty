package io.github.md5sha256.realty.economy;

import net.democracycraft.treasury.api.TreasuryApi;
import net.democracycraft.treasury.model.economy.Account;
import net.democracycraft.treasury.model.economy.AccountType;
import net.democracycraft.treasury.model.economy.TransferRequest;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Economy provider backed by Treasury. Provides full ledger support:
 * each transfer is recorded with a human-readable message that appears
 * in the player's Treasury transaction history.
 * <p>
 * Account resolution: the payer is always resolved as a personal account
 * (created with starting balance if missing). The recipient is resolved
 * by preferring any non-personal account (government/business) owned by
 * that UUID, falling back to a personal account.  This correctly handles
 * authority UUIDs that correspond to government Treasury accounts.
 */
public final class TreasuryEconomyProvider implements EconomyProvider {

    private static final String PLUGIN_SYSTEM = "realty";

    private final TreasuryApi treasuryApi;

    public TreasuryEconomyProvider(@NotNull TreasuryApi treasuryApi) {
        this.treasuryApi = treasuryApi;
    }

    @Override
    public double getBalance(@NotNull UUID playerId) {
        if (!treasuryApi.hasAccountByOwnerUuid(playerId)) {
            return 0.0;
        }
        BigDecimal balance = treasuryApi.getBalanceByOwnerUuid(playerId);
        return balance != null ? balance.doubleValue() : 0.0;
    }

    @Override
    public @NotNull PaymentResult transfer(@NotNull UUID fromId, @NotNull UUID toId,
                                            double amount, @NotNull String ledgerMessage) {
        try {
            Account payer = treasuryApi.resolveOrCreatePersonal(fromId);
            Account recipient = resolveRecipientAccount(toId);
            treasuryApi.transfer(new TransferRequest(
                    payer.getAccountId(),
                    recipient.getAccountId(),
                    BigDecimal.valueOf(amount),
                    ledgerMessage,
                    fromId,
                    null,
                    PLUGIN_SYSTEM,
                    null
            ));
            return new PaymentResult.Success();
        } catch (Exception e) {
            return new PaymentResult.Failure(e.getMessage() != null ? e.getMessage() : "Treasury transfer failed");
        }
    }

    @Override
    public @NotNull String formatAmount(double amount) {
        return treasuryApi.formatAmount(BigDecimal.valueOf(amount));
    }

    @Override
    public boolean hasLedgerSupport() {
        return true;
    }

    /**
     * Resolves the recipient's Treasury account. Prefers a government or business
     * account when one exists (e.g. for authority/landlord UUIDs tied to a town
     * or government entity), falling back to a personal account.
     */
    private @NotNull Account resolveRecipientAccount(@NotNull UUID ownerUuid) {
        List<Account> accounts = treasuryApi.getAccountsByOwner(ownerUuid);
        if (!accounts.isEmpty()) {
            // Prefer government > business > personal so that authority accounts
            // correctly route funds to the configured government treasury account.
            return accounts.stream()
                    .filter(a -> a.getAccountType() == AccountType.GOVERNMENT)
                    .findFirst()
                    .or(() -> accounts.stream()
                            .filter(a -> a.getAccountType() == AccountType.BUSINESS)
                            .findFirst())
                    .orElse(accounts.get(0));
        }
        return treasuryApi.resolveOrCreatePersonal(ownerUuid);
    }
}
