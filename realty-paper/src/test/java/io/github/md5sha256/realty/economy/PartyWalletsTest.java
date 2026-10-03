package io.github.md5sha256.realty.economy;

import io.github.md5sha256.realty.api.AccountKind;
import io.github.md5sha256.realty.api.Party;
import net.democracycraft.treasury.api.TreasuryApi;
import net.democracycraft.treasury.model.economy.Account;
import net.democracycraft.treasury.model.economy.AccountType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PartyWalletsTest {

    @Mock
    private TreasuryApi treasuryApi;

    private PartyWallets wallets;

    private final UUID player = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        wallets = new PartyWallets(treasuryApi);
    }

    private Account account(int id, AccountType type, UUID owner) {
        Account a = new Account();
        a.setAccountId(id);
        a.setAccountType(type);
        a.setOwnerUuid(owner);
        return a;
    }

    @Test
    void personal_usesThePersonalAccount_evenWhenThePlayerOwnsAGovernmentAccount() throws Exception {
        // A legacy government entity owns a GOVERNMENT account under its player UUID.
        // As a Personal party it still pays and is paid through its PERSONAL account.
        when(treasuryApi.resolveOrCreatePersonal(player)).thenReturn(account(13, AccountType.PERSONAL, player));

        assertEquals(13, wallets.forPayment(new Party.Personal(player)).getAccountId());
        verify(treasuryApi, never()).getAccountsByOwner(any());
    }

    @Test
    void account_usesTheNamedAccount() throws Exception {
        when(treasuryApi.getAccountById(42)).thenReturn(account(42, AccountType.GOVERNMENT, player));

        assertEquals(42, wallets.forPayment(new Party.Account(42, AccountKind.GOVERNMENT)).getAccountId());
    }

    @Test
    void group_usesTheMappedAccount() throws Exception {
        when(treasuryApi.getAccountById(42)).thenReturn(account(42, AccountType.GOVERNMENT, player));

        assertEquals(42, wallets.forPayment(new Party.Group("police", 42, AccountKind.GOVERNMENT)).getAccountId());
    }

    @Test
    void missingAccount_fails() {
        when(treasuryApi.getAccountById(42)).thenReturn(null);

        PartyWallets.WalletUnavailable failure = assertThrows(PartyWallets.WalletUnavailable.class,
                () -> wallets.forPayment(new Party.Account(42, AccountKind.GOVERNMENT)));
        assertEquals("Account #42 no longer exists", failure.getMessage());
    }

    @Test
    void archivedAccount_fails() {
        Account archived = account(42, AccountType.GOVERNMENT, player);
        archived.setArchived(true);
        when(treasuryApi.getAccountById(42)).thenReturn(archived);

        PartyWallets.WalletUnavailable failure = assertThrows(PartyWallets.WalletUnavailable.class,
                () -> wallets.forPayment(new Party.Account(42, AccountKind.GOVERNMENT)));
        assertEquals("Account #42 is archived", failure.getMessage());
    }

    @Test
    void kindMismatch_fails() {
        when(treasuryApi.getAccountById(42)).thenReturn(account(42, AccountType.BUSINESS, player));

        PartyWallets.WalletUnavailable failure = assertThrows(PartyWallets.WalletUnavailable.class,
                () -> wallets.forPayment(new Party.Account(42, AccountKind.GOVERNMENT)));
        assertEquals("Account #42 is no longer a government account", failure.getMessage());
    }

    @Test
    void accountThatNowRequiresAuthorization_fails() {
        // Treasury rejects a transfer from such an account without an authorizer, and
        // Realty never sends one, so the payment is refused before it is attempted.
        Account guarded = account(42, AccountType.GOVERNMENT, player);
        guarded.setRequiresAuthorization(true);
        when(treasuryApi.getAccountById(42)).thenReturn(guarded);

        PartyWallets.WalletUnavailable failure = assertThrows(PartyWallets.WalletUnavailable.class,
                () -> wallets.forPayment(new Party.Group("police", 42, AccountKind.GOVERNMENT)));
        assertEquals("Account #42 requires authorization", failure.getMessage());
    }

    @Test
    void balanceOfPlayerWithNoAccount_createsNothing() throws Exception {
        when(treasuryApi.getAccountsByTypeAndOwner(AccountType.PERSONAL, player)).thenReturn(List.of());

        assertNull(wallets.forBalance(new Party.Personal(player)));
        // A balance read must never have the side effect of opening an account.
        verify(treasuryApi, never()).resolveOrCreatePersonal(any());
    }

    @Test
    void balanceOfAccount_isCheckedLikeAPayment() {
        Account archived = account(42, AccountType.GOVERNMENT, player);
        archived.setArchived(true);
        when(treasuryApi.getAccountById(42)).thenReturn(archived);

        PartyWallets.WalletUnavailable failure = assertThrows(PartyWallets.WalletUnavailable.class,
                () -> wallets.forBalance(new Party.Account(42, AccountKind.GOVERNMENT)));
        assertEquals("Account #42 is archived", failure.getMessage());
    }
}
