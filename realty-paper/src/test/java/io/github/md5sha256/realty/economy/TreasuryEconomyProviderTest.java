package io.github.md5sha256.realty.economy;

import io.github.md5sha256.realty.api.AccountKind;
import io.github.md5sha256.realty.api.Party;
import net.democracycraft.treasury.api.TreasuryApi;
import net.democracycraft.treasury.model.economy.Account;
import net.democracycraft.treasury.model.economy.AccountType;
import net.democracycraft.treasury.model.economy.TransferRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TreasuryEconomyProviderTest {

    @Mock
    private TreasuryApi treasuryApi;

    private TreasuryEconomyProvider provider;

    private final UUID payer = UUID.randomUUID();
    private final Party.Account government = Party.account(42, AccountKind.GOVERNMENT);

    @BeforeEach
    void setUp() {
        provider = new TreasuryEconomyProvider(treasuryApi, new PartyWallets(treasuryApi));
    }

    private Account account(int id, AccountType type, UUID owner) {
        Account a = new Account();
        a.setAccountId(id);
        a.setAccountType(type);
        a.setOwnerUuid(owner);
        return a;
    }

    /** Stubs a payment from {@link #payer}'s PERSONAL account (1) into GOVERNMENT account 42. */
    private TransferRequest pay(double amount, UUID initiator) {
        when(treasuryApi.resolveOrCreatePersonal(payer)).thenReturn(account(1, AccountType.PERSONAL, payer));
        when(treasuryApi.getAccountById(42)).thenReturn(account(42, AccountType.GOVERNMENT, UUID.randomUUID()));
        when(treasuryApi.transfer(any())).thenReturn(99L);

        PaymentResult result = provider.transfer(Party.personal(payer), government, amount,
                "Rental Payment: REGION", initiator);
        assertInstanceOf(PaymentResult.Success.class, result);

        ArgumentCaptor<TransferRequest> req = ArgumentCaptor.forClass(TransferRequest.class);
        verify(treasuryApi).transfer(req.capture());
        return req.getValue();
    }

    @Test
    void transfer_playerToAccount_movesBetweenTheRightAccounts() {
        TransferRequest request = pay(50.0, payer);

        assertEquals(1, request.fromAccountId());
        assertEquals(42, request.toAccountId());
        // amount() is normalised to scale 2; compareTo is scale-insensitive.
        assertEquals(0, new BigDecimal("50.00").compareTo(request.amount()));
        assertEquals("Rental Payment: REGION", request.message());
        assertEquals("realty", request.pluginSystem());
    }

    @Test
    void transfer_passesTheInitiator() {
        UUID initiator = UUID.randomUUID();
        TransferRequest request = pay(50.0, initiator);

        assertEquals(initiator, request.initiator());
        assertNull(request.authorizer());
    }

    @Test
    void transfer_withoutInitiator_sendsTheSystemInitiator() {
        // Treasury's ledger cannot store a null initiator, so a scheduled payment names Realty itself.
        TransferRequest request = pay(50.0, null);

        assertEquals(TreasuryEconomyProvider.SYSTEM_INITIATOR, request.initiator());
        assertEquals(UUID.nameUUIDFromBytes("realty:system".getBytes(StandardCharsets.UTF_8)),
                request.initiator());
    }

    @Test
    void transfer_toUnavailableAccount_failsAndMovesNothing() {
        Account archived = account(42, AccountType.GOVERNMENT, UUID.randomUUID());
        archived.setArchived(true);
        when(treasuryApi.resolveOrCreatePersonal(payer)).thenReturn(account(1, AccountType.PERSONAL, payer));
        when(treasuryApi.getAccountById(42)).thenReturn(archived);

        PaymentResult result = provider.transfer(Party.personal(payer), government, 50.0,
                "Rental Payment: REGION", payer);

        assertEquals(new PaymentResult.Failure("Account #42 is archived"), result);
        verify(treasuryApi, never()).transfer(any());
    }

    @Test
    void transfer_fromUnavailableAccount_failsAndMovesNothing() {
        // A refund out of an account that Treasury has since deleted.
        when(treasuryApi.getAccountById(42)).thenReturn(null);

        PaymentResult result = provider.transfer(government, Party.personal(payer), 50.0,
                "Early Lease Termination Refund: REGION", payer);

        assertEquals(new PaymentResult.Failure("Account #42 no longer exists"), result);
        verify(treasuryApi, never()).transfer(any());
    }

    @Test
    void transfer_roundsToTwoDecimals() {
        // Pro-rata refunds (price * remaining / total) can carry more than 2 decimals,
        // which Treasury rejects.
        TransferRequest request = pay(10.005, payer);

        assertEquals(new BigDecimal("10.01"), request.amount());
    }

    @Test
    void balance_readsTheAccountTheTransferWouldUse() {
        when(treasuryApi.getAccountById(42)).thenReturn(account(42, AccountType.GOVERNMENT, payer));
        when(treasuryApi.getBalanceByAccountId(42)).thenReturn(new BigDecimal("250.00"));
        when(treasuryApi.getAccountsByTypeAndOwner(AccountType.PERSONAL, payer))
                .thenReturn(List.of(account(13, AccountType.PERSONAL, payer)));
        when(treasuryApi.getBalanceByAccountId(13)).thenReturn(new BigDecimal("10.50"));

        assertEquals(250.0, provider.getBalance(government));
        // The player also owns account 42, but as a Personal party only the PERSONAL account counts.
        assertEquals(10.50, provider.getBalance(Party.personal(payer)));
    }

    @Test
    void balanceOfPlayerWithNoAccount_isZeroAndCreatesNothing() {
        when(treasuryApi.getAccountsByTypeAndOwner(AccountType.PERSONAL, payer)).thenReturn(List.of());

        assertEquals(0.0, provider.getBalance(Party.personal(payer)));
        verify(treasuryApi, never()).resolveOrCreatePersonal(any());
    }

    @Test
    void balanceOfUnavailableAccount_isZero() {
        when(treasuryApi.getAccountById(42)).thenReturn(null);

        assertEquals(0.0, provider.getBalance(government));
    }

    @Test
    void balanceOfNull_isZero() {
        when(treasuryApi.getAccountsByTypeAndOwner(AccountType.PERSONAL, payer))
                .thenReturn(List.of(account(13, AccountType.PERSONAL, payer)));
        when(treasuryApi.getBalanceByAccountId(13)).thenReturn(null);

        assertEquals(0.0, provider.getBalance(Party.personal(payer)));
    }
}
