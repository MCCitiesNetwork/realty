package io.github.md5sha256.realty.economy;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Economy provider backed by Vault. Used when Treasury is not present.
 * Ledger messages are discarded (Vault has no per-transaction metadata support).
 * Tax collection is not available without Treasury.
 */
public final class VaultEconomyProvider implements EconomyProvider {

    private final Economy economy;

    public VaultEconomyProvider(@NotNull Economy economy) {
        this.economy = economy;
    }

    @Override
    public double getBalance(@NotNull UUID playerId) {
        return economy.getBalance(Bukkit.getOfflinePlayer(playerId));
    }

    @Override
    public @NotNull PaymentResult transfer(@NotNull UUID fromId, @NotNull UUID toId,
                                            double amount, @NotNull String ledgerMessage) {
        OfflinePlayer payer = Bukkit.getOfflinePlayer(fromId);
        EconomyResponse withdraw = economy.withdrawPlayer(payer, amount);
        if (!withdraw.transactionSuccess()) {
            return new PaymentResult.Failure(withdraw.errorMessage);
        }
        OfflinePlayer recipient = Bukkit.getOfflinePlayer(toId);
        EconomyResponse deposit = economy.depositPlayer(recipient, amount);
        if (!deposit.transactionSuccess()) {
            // Rollback: return money to payer
            economy.depositPlayer(payer, amount);
            return new PaymentResult.Failure(deposit.errorMessage);
        }
        return new PaymentResult.Success();
    }

    @Override
    public @NotNull String formatAmount(double amount) {
        return economy.format(amount);
    }

    @Override
    public boolean hasLedgerSupport() {
        return false;
    }
}
