package io.github.md5sha256.realty.economy;

import io.github.md5sha256.realty.api.Party;
import net.democracycraft.treasury.api.TreasuryApi;
import net.democracycraft.treasury.model.economy.Account;
import net.democracycraft.treasury.model.economy.TransferRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Economy provider backed by Treasury. Provides full ledger support:
 * each transfer is recorded with a human-readable message and the player
 * who caused it.
 * <p>
 * {@link PartyWallets} decides which account each party pays from and into.
 * A balance read uses the same account, so an affordability check always
 * inspects the account the subsequent transfer would actually touch.
 */
public final class TreasuryEconomyProvider implements EconomyProvider {

    private static final String PLUGIN_SYSTEM = "realty";

    /**
     * The initiator recorded for a payment no player caused, such as a scheduled
     * termination refund. Treasury's ledger cannot store a missing initiator.
     * No account exists for it.
     */
    static final UUID SYSTEM_INITIATOR = UUID.nameUUIDFromBytes("realty:system".getBytes(StandardCharsets.UTF_8));

    private final TreasuryApi treasuryApi;
    private final PartyWallets wallets;

    public TreasuryEconomyProvider(@NotNull TreasuryApi treasuryApi, @NotNull PartyWallets wallets) {
        this.treasuryApi = treasuryApi;
        this.wallets = wallets;
    }

    @Override
    public double getBalance(@NotNull Party party) {
        Account account;
        try {
            // A read must not open an account: a player with none has no funds.
            account = wallets.forBalance(party);
        } catch (PartyWallets.WalletUnavailable e) {
            return 0.0;
        }
        if (account == null) {
            return 0.0;
        }
        BigDecimal balance = treasuryApi.getBalanceByAccountId(account.getAccountId());
        return balance != null ? balance.doubleValue() : 0.0;
    }

    @Override
    public @NotNull PaymentResult transfer(@NotNull Party from, @NotNull Party to, double amount,
                                           @NotNull String ledgerMessage, @Nullable UUID initiator) {
        try {
            Account payer = wallets.forPayment(from);
            Account recipient = wallets.forPayment(to);
            // Treasury rejects amounts with more than 2 decimal places. Amounts
            // derived from arithmetic (e.g. pro-rata refunds: price * remaining /
            // total) can carry extra precision, so normalise to 2 decimals here.
            BigDecimal normalisedAmount = BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP);
            treasuryApi.transfer(new TransferRequest(
                    payer.getAccountId(),
                    recipient.getAccountId(),
                    normalisedAmount,
                    ledgerMessage,
                    initiator != null ? initiator : SYSTEM_INITIATOR,
                    null,
                    PLUGIN_SYSTEM,
                    null
            ));
            return new PaymentResult.Success();
        } catch (PartyWallets.WalletUnavailable e) {
            return new PaymentResult.Failure(e.getMessage());
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
}
