package io.github.md5sha256.realty.economy;

import io.github.md5sha256.realty.api.AccountKind;
import io.github.md5sha256.realty.api.Party;
import net.democracycraft.treasury.api.TreasuryApi;
import net.democracycraft.treasury.model.economy.Account;
import net.democracycraft.treasury.model.economy.AccountType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * Finds the Treasury account a party pays from and is paid into.
 * <p>
 * A {@link Party.Personal} always uses the player's PERSONAL account, even when
 * the player also owns a government or business account. An account or group
 * party uses the account it names, and only while Treasury still holds that
 * account as it was assigned: present, not archived, of the stored kind, and not
 * requiring authorization (Realty never sends an authorizer, so Treasury would
 * reject the transfer). Treasury does not check the first two itself.
 */
public final class PartyWallets {

    private final TreasuryApi treasury;

    public PartyWallets(@NotNull TreasuryApi treasury) {
        this.treasury = treasury;
    }

    /**
     * The account a payment uses. Creates the PERSONAL account of a player who has none.
     */
    public @NotNull Account forPayment(@NotNull Party party) throws WalletUnavailable {
        return switch (party) {
            case Party.Personal personal -> treasury.resolveOrCreatePersonal(personal.playerUuid());
            case Party.Account account -> namedAccount(account.accountId(), account.kind());
            case Party.Group group -> namedAccount(group.accountId(), group.accountKind());
        };
    }

    /**
     * The account a balance is read from. Creates nothing; null for a player with no PERSONAL account.
     */
    public @Nullable Account forBalance(@NotNull Party party) throws WalletUnavailable {
        return switch (party) {
            case Party.Personal personal -> {
                List<Account> accounts = treasury.getAccountsByTypeAndOwner(AccountType.PERSONAL,
                        personal.playerUuid());
                yield accounts.isEmpty() ? null : accounts.getFirst();
            }
            case Party.Account account -> namedAccount(account.accountId(), account.kind());
            case Party.Group group -> namedAccount(group.accountId(), group.accountKind());
        };
    }

    private @NotNull Account namedAccount(int accountId, @NotNull AccountKind storedKind) throws WalletUnavailable {
        Account account = treasury.getAccountById(accountId);
        if (account == null) {
            throw new WalletUnavailable("Account #" + accountId + " no longer exists");
        }
        if (account.isArchived()) {
            throw new WalletUnavailable("Account #" + accountId + " is archived");
        }
        if (account.getAccountType() != AccountType.valueOf(storedKind.name())) {
            throw new WalletUnavailable("Account #" + accountId + " is no longer a "
                    + storedKind.name().toLowerCase(Locale.ROOT) + " account");
        }
        if (account.isRequiresAuthorization()) {
            throw new WalletUnavailable("Account #" + accountId + " requires authorization");
        }
        return account;
    }

    /**
     * The account a party names cannot be used. The message is shown to the player.
     */
    public static final class WalletUnavailable extends Exception {

        public WalletUnavailable(@NotNull String message) {
            super(message);
        }
    }
}
