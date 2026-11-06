package io.github.md5sha256.realty.economy;

import io.github.md5sha256.realty.api.AccountKind;
import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.settings.DefaultParties;
import io.paradaux.treasury.api.TreasuryApi;
import io.paradaux.treasury.model.economy.Account;
import io.paradaux.treasury.model.economy.AccountType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HoldingAccountTest {

    private static final UUID OWNER = UUID.nameUUIDFromBytes("realty:holding".getBytes(StandardCharsets.UTF_8));
    private static final Logger LOG = Logger.getLogger("HoldingAccountTest");

    @Mock
    private TreasuryApi treasury;

    private static Account systemAccount(int id, boolean allowOverdraft) {
        return new Account(id, AccountType.SYSTEM, OWNER, "Realty Holding", false, false, allowOverdraft, null);
    }

    @Test
    void holdingAccount_treasury_reusesExistingSystemAccount_turnsOverdraftOff() {
        Account existing = systemAccount(17, true);
        when(treasury.getAccountsByTypeAndOwner(AccountType.SYSTEM, OWNER)).thenReturn(List.of(existing));

        HoldingAccount holding = TreasuryHoldingAccount.create(treasury, LOG);

        Assertions.assertEquals(Optional.of(Party.account(17, AccountKind.SYSTEM)), holding.party());
        verify(treasury, never()).createAccount(any(AccountType.class), any(), any());
        ArgumentCaptor<Account> updated = ArgumentCaptor.forClass(Account.class);
        verify(treasury).updateAccount(updated.capture());
        Assertions.assertEquals(17, updated.getValue().getAccountId());
        Assertions.assertFalse(updated.getValue().isAllowOverdraft());
    }

    @Test
    void holdingAccount_treasury_existingWithoutOverdraft_isLeftAlone() {
        when(treasury.getAccountsByTypeAndOwner(AccountType.SYSTEM, OWNER)).thenReturn(List.of(systemAccount(17, false)));

        HoldingAccount holding = TreasuryHoldingAccount.create(treasury, LOG);

        Assertions.assertEquals(Optional.of(Party.account(17, AccountKind.SYSTEM)), holding.party());
        verify(treasury, never()).updateAccount(any());
    }

    @Test
    void holdingAccount_treasury_createsTheAccountWhenMissing() {
        when(treasury.getAccountsByTypeAndOwner(AccountType.SYSTEM, OWNER)).thenReturn(List.of());
        when(treasury.createAccount(AccountType.SYSTEM, OWNER, "Realty Holding")).thenReturn(systemAccount(23, false));

        HoldingAccount holding = TreasuryHoldingAccount.create(treasury, LOG);

        Assertions.assertEquals(Optional.of(Party.account(23, AccountKind.SYSTEM)), holding.party());
    }

    @Test
    void holdingAccount_vault_withoutEscrow_isEmpty() {
        HoldingAccount holding = HoldingAccount.vault(DefaultParties::unresolved);

        Assertions.assertEquals(Optional.empty(), holding.party());
    }

    @Test
    void holdingAccount_vault_withEscrow_isThatPlayer_readOnEachUse() {
        UUID banker = UUID.randomUUID();
        DefaultParties[] current = {DefaultParties.unresolved()};
        HoldingAccount holding = HoldingAccount.vault(() -> current[0]);
        Assertions.assertTrue(holding.party().isEmpty());

        current[0] = new DefaultParties(null, null, null, false, banker, List.of());

        Assertions.assertEquals(Optional.of(Party.personal(banker)), holding.party());
    }

    // --- Usable: the account exists and can be paid into and out of ---

    @Test
    void liveTreasuryAccount_isUsable() {
        Account account = systemAccount(17, false);
        when(treasury.getAccountsByTypeAndOwner(AccountType.SYSTEM, OWNER)).thenReturn(List.of(account));
        when(treasury.getAccountById(17)).thenReturn(account);

        HoldingAccount holding = TreasuryHoldingAccount.create(treasury, LOG);

        Assertions.assertEquals(new HoldingAccount.Usable(Party.account(17, AccountKind.SYSTEM)), holding.usable());
    }

    @Test
    void archivedTreasuryAccount_isUnusable() {
        Account account = systemAccount(17, false);
        when(treasury.getAccountsByTypeAndOwner(AccountType.SYSTEM, OWNER)).thenReturn(List.of(account));
        HoldingAccount holding = TreasuryHoldingAccount.create(treasury, LOG);
        Account archived = systemAccount(17, false).withArchived(true);
        when(treasury.getAccountById(17)).thenReturn(archived);

        HoldingAccount.Unusable unusable =
                Assertions.assertInstanceOf(HoldingAccount.Unusable.class, holding.usable());

        Assertions.assertEquals("Account #17 is archived", unusable.reason());
    }

    @Test
    void treasuryAccount_requiringAuthorization_orGone_isUnusable() {
        when(treasury.getAccountsByTypeAndOwner(AccountType.SYSTEM, OWNER)).thenReturn(List.of(systemAccount(17, false)));
        long[] now = {0};
        HoldingAccount holding = TreasuryHoldingAccount.create(treasury, LOG, () -> now[0]);
        Account locked = systemAccount(17, false).withRequiresAuthorization(true);
        when(treasury.getAccountById(17)).thenReturn(locked);
        Assertions.assertEquals(new HoldingAccount.Unusable("Account #17 requires authorization"), holding.usable());

        now[0] += HoldingAccount.RECHECK_AFTER.toNanos();
        when(treasury.getAccountById(17)).thenReturn(null);
        Assertions.assertEquals(new HoldingAccount.Unusable("Account #17 no longer exists"), holding.usable());
    }

    @Test
    void treasuryUnreachable_isUnusable() {
        when(treasury.getAccountsByTypeAndOwner(AccountType.SYSTEM, OWNER)).thenReturn(List.of(systemAccount(17, false)));
        HoldingAccount holding = TreasuryHoldingAccount.create(treasury, LOG);
        when(treasury.getAccountById(17)).thenThrow(new IllegalStateException("connection refused"));

        HoldingAccount.Unusable unusable =
                Assertions.assertInstanceOf(HoldingAccount.Unusable.class, holding.usable());

        Assertions.assertTrue(unusable.reason().contains("connection refused"), unusable.reason());
    }

    @Test
    void treasuryCheck_isCachedBriefly_thenRepeated() {
        Account account = systemAccount(17, false);
        when(treasury.getAccountsByTypeAndOwner(AccountType.SYSTEM, OWNER)).thenReturn(List.of(account));
        when(treasury.getAccountById(17)).thenReturn(account);
        long[] now = {0};
        HoldingAccount holding = TreasuryHoldingAccount.create(treasury, LOG, () -> now[0]);

        holding.usable();
        holding.usable();
        verify(treasury, org.mockito.Mockito.times(1)).getAccountById(17);

        now[0] += HoldingAccount.RECHECK_AFTER.toNanos();
        holding.usable();
        verify(treasury, org.mockito.Mockito.times(2)).getAccountById(17);
    }

    @Test
    void vaultWithoutEscrow_isUnusable() {
        HoldingAccount holding = HoldingAccount.vault(DefaultParties::unresolved);

        Assertions.assertEquals(new HoldingAccount.Unusable("no escrow player is set in settings.yml"),
                holding.usable());
    }

    @Test
    void vaultWithEscrow_isUsable() {
        UUID banker = UUID.randomUUID();
        HoldingAccount holding = HoldingAccount.vault(
                () -> new DefaultParties(null, null, null, false, banker, List.of()));

        Assertions.assertEquals(new HoldingAccount.Usable(Party.personal(banker)), holding.usable());
    }

    @Test
    void none_isUnusable_withItsReason() {
        Assertions.assertEquals(new HoldingAccount.Unusable("database down"), HoldingAccount.none("database down").usable());
    }
}
