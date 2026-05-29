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
 * (created with starting balance if missing). The recipient is resolved by
 * preferring its PERSONAL account — rental/sale income belongs to the
 * landlord personally, even if they happen to own a firm. Only when the
 * recipient has no personal account (a synthetic authority UUID backing a
 * government entity) do we fall back to a government/business account.
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
     * Resolves the recipient's Treasury account.
     * <p>
     * A real player landlord always owns a PERSONAL account (Treasury enforces
     * one per player), so we prefer it: their rental/sale income must land in
     * their personal balance, never in a firm BUSINESS account they happen to
     * own (firm accounts are owned by the proprietor's own UUID, which is how
     * such funds previously leaked into business accounts).
     * <p>
     * Only when the recipient has no personal account — i.e. a synthetic
     * authority UUID that backs a government entity — do we fall back to the
     * prior government &gt; business &gt; first-available ordering so authority
     * payments still route to the configured government treasury account.
     */
    private @NotNull Account resolveRecipientAccount(@NotNull UUID ownerUuid) {
        List<Account> accounts = treasuryApi.getAccountsByOwner(ownerUuid);
        if (!accounts.isEmpty()) {
            return accounts.stream()
                    .filter(a -> a.getAccountType() == AccountType.PERSONAL)
                    .findFirst()
                    .or(() -> accounts.stream()
                            .filter(a -> a.getAccountType() == AccountType.GOVERNMENT)
                            .findFirst())
                    .or(() -> accounts.stream()
                            .filter(a -> a.getAccountType() == AccountType.BUSINESS)
                            .findFirst())
                    .orElse(accounts.get(0));
        }
        return treasuryApi.resolveOrCreatePersonal(ownerUuid);
    }
}
