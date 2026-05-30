package io.github.md5sha256.realty.economy;

import net.democracycraft.treasury.api.TreasuryApi;
import net.democracycraft.treasury.model.economy.Account;
import net.democracycraft.treasury.model.economy.AccountType;
import net.democracycraft.treasury.model.economy.TransferRequest;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

/**
 * Economy provider backed by Treasury. Provides full ledger support:
 * each transfer is recorded with a human-readable message that appears
 * in the player's Treasury transaction history.
 * <p>
 * Account resolution: the payer is always resolved as a personal account
 * (created with starting balance if missing). The recipient is resolved by
 * preferring its GOVERNMENT account, then PERSONAL, then BUSINESS — so
 * government landlords (legacy DCGovernment-style real UUIDs that own both a
 * personal and a government account) route income to their government
 * treasury, while ordinary landlords still get their personal balance rather
 * than a firm BUSINESS account they happen to own.
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
            // Treasury rejects amounts with more than 2 decimal places. Amounts
            // derived from arithmetic (e.g. pro-rata refunds: price * remaining /
            // total) can carry extra precision, so normalise to 2 decimals here.
            BigDecimal normalisedAmount = BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP);
            treasuryApi.transfer(new TransferRequest(
                    payer.getAccountId(),
                    recipient.getAccountId(),
                    normalisedAmount,
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
     * Resolves the recipient's Treasury account, preferring
     * GOVERNMENT &gt; PERSONAL &gt; BUSINESS &gt; first-available.
     * <p>
     * GOVERNMENT wins first because legacy government entities (e.g.
     * DCGovernment) are real Minecraft accounts whose UUID owns <em>both</em> a
     * personal and a government account; their leasehold income must land in
     * the government treasury, not the player's personal balance.
     * <p>
     * Ordinary landlords have no government account, so PERSONAL is chosen next:
     * rental/sale income belongs to them personally, never a firm BUSINESS
     * account they happen to own (firm accounts are owned by the proprietor's
     * own UUID, which is how such funds previously leaked into business
     * accounts).
     * <p>
     * When the recipient has no account at all, resolve-or-create their personal
     * account.
     */
    private @NotNull Account resolveRecipientAccount(@NotNull UUID ownerUuid) {
        List<Account> accounts = treasuryApi.getAccountsByOwner(ownerUuid);
        if (!accounts.isEmpty()) {
            return accounts.stream()
                    .filter(a -> a.getAccountType() == AccountType.GOVERNMENT)
                    .findFirst()
                    .or(() -> accounts.stream()
                            .filter(a -> a.getAccountType() == AccountType.PERSONAL)
                            .findFirst())
                    .or(() -> accounts.stream()
                            .filter(a -> a.getAccountType() == AccountType.BUSINESS)
                            .findFirst())
                    .orElse(accounts.get(0));
        }
        return treasuryApi.resolveOrCreatePersonal(ownerUuid);
    }
}
