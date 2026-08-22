package io.github.md5sha256.realty.economy;

import net.democracycraft.treasury.api.TreasuryApi;
import net.democracycraft.treasury.model.economy.Account;
import net.democracycraft.treasury.model.economy.AccountType;
import net.democracycraft.treasury.model.economy.TransferRequest;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Economy provider backed by Treasury. Provides full ledger support:
 * each transfer is recorded with a human-readable message that appears
 * in the player's Treasury transaction history.
 * <p>
 * Account resolution is the same on both sides of a transfer: prefer the
 * party's GOVERNMENT account, then PERSONAL, then BUSINESS. Government
 * entities (legacy DCGovernment-style real UUIDs that own both a personal and
 * a government account) therefore both receive income into and pay refunds out
 * of their government treasury, while ordinary players resolve to their
 * personal balance rather than a firm BUSINESS account they happen to own.
 * Balance reads follow the same preference, so an affordability check always
 * inspects the account the subsequent transfer would actually touch.
 */
public final class TreasuryEconomyProvider implements EconomyProvider {

    private static final String PLUGIN_SYSTEM = "realty";

    private final TreasuryApi treasuryApi;

    public TreasuryEconomyProvider(@NotNull TreasuryApi treasuryApi) {
        this.treasuryApi = treasuryApi;
    }

    @Override
    public double getBalance(@NotNull UUID playerId) {
        // Read the same account transfer() would debit, not whichever one
        // getBalanceByOwnerUuid happens to pick -- otherwise a government
        // entity is checked for affordability against its personal balance.
        // A read must not open an account, so there is no create-if-missing
        // fallback here: no accounts means no funds.
        Account account = preferredAccount(treasuryApi.getAccountsByOwner(playerId)).orElse(null);
        if (account == null) {
            return 0.0;
        }
        BigDecimal balance = treasuryApi.getBalanceByAccountId(account.getAccountId());
        return balance != null ? balance.doubleValue() : 0.0;
    }

    @Override
    public @NotNull PaymentResult transfer(@NotNull UUID fromId, @NotNull UUID toId,
                                            double amount, @NotNull String ledgerMessage) {
        try {
            // Both sides resolve identically: a refund from a government landlord
            // must leave the same account the rent was paid into.
            Account payer = resolveAccount(fromId);
            Account recipient = resolveAccount(toId);
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
     * Resolves a party's Treasury account, preferring
     * GOVERNMENT &gt; PERSONAL &gt; BUSINESS &gt; first-available.
     * <p>
     * GOVERNMENT wins first because legacy government entities (e.g.
     * DCGovernment) are real Minecraft accounts whose UUID owns <em>both</em> a
     * personal and a government account; their leasehold income must land in
     * the government treasury, not the player's personal balance — and, on the
     * paying side, a lease-termination refund must be debited from that same
     * treasury rather than the entity's personal balance.
     * <p>
     * Ordinary players have no government account, so PERSONAL is chosen next:
     * rental/sale income belongs to them personally, never a firm BUSINESS
     * account they happen to own (firm accounts are owned by the proprietor's
     * own UUID, which is how such funds previously leaked into business
     * accounts).
     * <p>
     * When the party has no account at all, resolve-or-create their personal
     * account.
     */
    private @NotNull Account resolveAccount(@NotNull UUID ownerUuid) {
        return preferredAccount(treasuryApi.getAccountsByOwner(ownerUuid))
                .orElseGet(() -> treasuryApi.resolveOrCreatePersonal(ownerUuid));
    }

    /**
     * Applies the GOVERNMENT &gt; PERSONAL &gt; BUSINESS &gt; first-available
     * preference to an already-fetched account list, or empty when the party
     * holds no accounts at all. Shared by {@link #resolveAccount(UUID)} and
     * {@link #getBalance(UUID)} so a balance check and the transfer it gates
     * can never disagree about which account is in play.
     */
    private @NotNull Optional<Account> preferredAccount(@NotNull List<Account> accounts) {
        if (accounts.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(accounts.stream()
                .filter(a -> a.getAccountType() == AccountType.GOVERNMENT)
                .findFirst()
                .or(() -> accounts.stream()
                        .filter(a -> a.getAccountType() == AccountType.PERSONAL)
                        .findFirst())
                .or(() -> accounts.stream()
                        .filter(a -> a.getAccountType() == AccountType.BUSINESS)
                        .findFirst())
                .orElse(accounts.get(0)));
    }
}
