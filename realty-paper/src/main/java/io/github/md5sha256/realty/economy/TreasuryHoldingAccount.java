package io.github.md5sha256.realty.economy;

import io.github.md5sha256.realty.api.AccountKind;
import io.github.md5sha256.realty.api.Party;
import io.paradaux.treasury.api.TreasuryApi;
import io.paradaux.treasury.model.economy.Account;
import io.paradaux.treasury.model.economy.AccountType;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.logging.Logger;

/**
 * Sets up Realty's Treasury SYSTEM account as the {@link HoldingAccount}. Kept apart so that only a
 * server running Treasury loads Treasury's types.
 */
public final class TreasuryHoldingAccount {

    /** The owner of Realty's SYSTEM account. No player has this id. */
    static final UUID OWNER = UUID.nameUUIDFromBytes("realty:holding".getBytes(StandardCharsets.UTF_8));
    static final String NAME = "Realty Holding";

    private TreasuryHoldingAccount() {
    }

    /**
     * Finds Realty's SYSTEM account, or creates it. An account found with overdraft allowed has it
     * turned off, so it can never pay out more than it holds. Does Treasury I/O.
     *
     * <p>The account is usable while Treasury answers and the account exists, is not archived, is
     * still a SYSTEM account and needs no authorization, as {@link PartyWallets} requires of any
     * account a payment names.</p>
     */
    public static @NotNull HoldingAccount create(@NotNull TreasuryApi api, @NotNull Logger log) {
        return create(api, log, System::nanoTime);
    }

    /** As {@link #create(TreasuryApi, Logger)}, caching each usability check by {@code nanoClock}. */
    static @NotNull HoldingAccount create(@NotNull TreasuryApi api, @NotNull Logger log,
                                          @NotNull LongSupplier nanoClock) {
        List<Account> existing = api.getAccountsByTypeAndOwner(AccountType.SYSTEM, OWNER).stream()
                .filter(account -> !account.isArchived())
                .toList();
        Account account;
        if (existing.isEmpty()) {
            account = api.createAccount(AccountType.SYSTEM, OWNER, NAME);
            log.info("Created the Treasury account '" + NAME + "' (#" + account.getAccountId()
                    + ") to hold instalments until a sale finishes");
        } else {
            account = existing.getFirst();
        }
        if (account.isAllowOverdraft()) {
            account = account.withAllowOverdraft(false);
            api.updateAccount(account);
            log.warning("The Treasury account '" + NAME + "' (#" + account.getAccountId()
                    + ") allowed overdraft; it has been turned off, so it can never pay out more than it holds");
        }
        Party party = Party.account(account.getAccountId(), AccountKind.SYSTEM);
        PartyWallets wallets = new PartyWallets(api);
        return HoldingAccount.checked(party, () -> check(wallets, party), nanoClock);
    }

    private static @NotNull HoldingAccount.Status check(@NotNull PartyWallets wallets, @NotNull Party party) {
        try {
            wallets.forBalance(party);
            return new HoldingAccount.Usable(party);
        } catch (PartyWallets.WalletUnavailable ex) {
            return new HoldingAccount.Unusable(ex.getMessage());
        } catch (RuntimeException ex) {
            return new HoldingAccount.Unusable("Treasury could not be asked about it: "
                    + (ex.getMessage() != null ? ex.getMessage() : ex.toString()));
        }
    }
}
